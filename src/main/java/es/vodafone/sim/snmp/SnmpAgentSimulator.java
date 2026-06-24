package es.vodafone.sim.snmp;

import org.snmp4j.*;
import org.snmp4j.mp.MPv1;
import org.snmp4j.mp.MPv2c;
import org.snmp4j.mp.MPv3;
import org.snmp4j.mp.StatusInformation;
import org.snmp4j.security.*;
import org.snmp4j.smi.*;
import org.snmp4j.transport.DefaultUdpTransportMapping;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.io.IOException;
import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;

@Service
public class SnmpAgentSimulator {

  // ── Configuración ──────────────────────────────────────────────
  private static final int    AGENT_COUNT = 500;
  private static final int    IF_COUNT    = 10;
  private static final int    PORT        = 1161;
  private static final String USER        = "simuser";
  private static final String AUTH_KEY    = "authpassword12";
  private static final String PRIV_KEY    = "privpassword12";
  private static final int    TICK_SECS   = 30;
  // ──────────────────────────────────────────────────────────────

  private final List<Snmp>                        snmpInstances   = new ArrayList<>();
  private final Map<Integer, List<InterfaceData>> agentInterfaces = new HashMap<>();
  private final ScheduledExecutorService          ticker          =
      Executors.newSingleThreadScheduledExecutor();

  // OIDs estáticos
  private static final String OID_SYS_DESCR  = "1.3.6.1.2.1.1.1.0";
  private static final String OID_SYS_NAME   = "1.3.6.1.2.1.1.5.0";
  private static final String OID_IF_NUMBER  = "1.3.6.1.2.1.2.1.0";
  private static final String OID_IF_TABLE   = "1.3.6.1.2.1.2.2.1.";
  private static final String OID_IF_X_TABLE = "1.3.6.1.2.1.31.1.1.1.";

  private static final Logger logger = Logger.getLogger(SnmpAgentSimulator.class.getName());

  /** Convierte índice 0-499 → "127.1.x.y" */
  private static String indexToIp(int index) {
    return "127.1." + (index / 254) + "." + (index % 254 + 1);
  }

  @PostConstruct
  public void start() throws IOException {
    // Solo registrar protocolos de seguridad en el singleton global —
    // los USM se crean por agente en buildAgent(), no aquí.
    SecurityProtocols.getInstance().addDefaultProtocols();
    SecurityProtocols.getInstance().addAuthenticationProtocol(new AuthSHA());
    SecurityProtocols.getInstance().addAuthenticationProtocol(new AuthHMAC192SHA256());
    SecurityProtocols.getInstance().addAuthenticationProtocol(new AuthHMAC384SHA512());

    SecurityProtocols.getInstance().addPrivacyProtocol(new PrivAES128());
    SecurityProtocols.getInstance().addPrivacyProtocol(new PrivAES192());
    SecurityProtocols.getInstance().addPrivacyProtocol(new PrivAES256());

    for (int i = 0; i < AGENT_COUNT; i++) {
      String ip = indexToIp(i);

      List<InterfaceData> ifaces = new ArrayList<>();
      for (int ifIdx = 1; ifIdx <= IF_COUNT; ifIdx++) {
        ifaces.add(new InterfaceData(i, ifIdx));
      }
      agentInterfaces.put(i, ifaces);

      snmpInstances.add(buildAgent(ip, i, ifaces));

      if ((i + 1) % 50 == 0) {
        logger.info(String.format("  Arrancados %d agentes...%n", i + 1));
      }
    }

    ticker.scheduleAtFixedRate(
        this::tickCounters, TICK_SECS, TICK_SECS, TimeUnit.SECONDS);

    logger.info(String.format("✓ %d agentes SNMPv3 con %d interfaces cada uno " +
        "escuchando en puerto %d%n", AGENT_COUNT, IF_COUNT, PORT));
  }

  private Snmp buildAgent(String ip, int index, List<InterfaceData> ifaces)
      throws IOException {

    // ── Fix 1: engineID único por agente ──────────────────────────
    byte[] engineId = MPv3.createLocalEngineID(new OctetString("agent-" + index));

    // ── Fix 2: USM propio por agente, NO el singleton global ──────
    USM usm = new USM(SecurityProtocols.getInstance(),
        new OctetString(engineId), 0);
    usm.addUser(new UsmUser(
        new OctetString(USER),
        AuthHMAC384SHA512.ID,    new OctetString(AUTH_KEY),
        PrivAES256.ID, new OctetString(PRIV_KEY)
    ));

    // ── Fix 3: SecurityModels propio por agente ───────────────────
    SecurityModels securityModels = new SecurityModels();
    securityModels.addSecurityModel(usm);

    // ── Fix 4: MPv3 con su USM aislado ───────────────────────────
    MPv3 mpv3 = new MPv3(engineId);
    mpv3.setSecurityModels(securityModels);

    // ── Fix 5: Dispatcher propio con el MPv3 correcto ────────────
    MessageDispatcher dispatcher = new MessageDispatcherImpl();
    dispatcher.addMessageProcessingModel(new MPv1());
    dispatcher.addMessageProcessingModel(new MPv2c());
    dispatcher.addMessageProcessingModel(mpv3);

    // ── Transport enlazado a la IP específica del agente ─────────
    DefaultUdpTransportMapping transport = new DefaultUdpTransportMapping(
        new UdpAddress(InetAddress.getByName(ip), PORT));

    // ── Snmp construido con el dispatcher propio ─────────────────
    Snmp snmp = new Snmp(dispatcher, transport);

    snmp.addCommandResponder(new CommandResponder() {
      @Override
      public <A extends Address> void processPdu(CommandResponderEvent<A> event) {
        if (event.getPDU() == null) return;
        if (!(event.getPeerAddress() instanceof UdpAddress)) return;

        ScopedPDU request  = (ScopedPDU) event.getPDU();
        ScopedPDU response = new ScopedPDU();
        response.setType(PDU.RESPONSE);
        response.setRequestID(request.getRequestID());
        response.setContextEngineID(request.getContextEngineID());
        response.setContextName(request.getContextName());

        for (VariableBinding vb : request.getVariableBindings()) {
          response.add(resolveOid(vb.getOid(), ip, index, ifaces));
        }

        StatusInformation statusInfo = new StatusInformation();
        try {
          snmp.getMessageDispatcher().returnResponsePdu(
              event.getMessageProcessingModel(),
              event.getSecurityModel(),
              event.getSecurityName(),
              event.getSecurityLevel(),
              response,
              event.getMaxSizeResponsePDU(),
              event.getStateReference(),
              statusInfo
          );
        } catch (MessageException e) {
          logger.severe("Error enviando respuesta [" + ip + "]: " + e.getMessage());
        }
      }
    });

    transport.listen();
    return snmp;
  }

  // ── Resolución de OIDs ─────────────────────────────────────────

  private VariableBinding resolveOid(OID oid, String ip, int agentIndex,
                                     List<InterfaceData> ifaces) {
    String oidStr = oid.toString();

    switch (oidStr) {
      case OID_SYS_DESCR -> {
        return new VariableBinding(oid,
            new OctetString("SimAgent-" + agentIndex + " [" + ip + "]"));
      }
      case OID_SYS_NAME -> {
        return new VariableBinding(oid, new OctetString("sim-" + ip));
      }
      case OID_IF_NUMBER -> {
        return new VariableBinding(oid, new Integer32(IF_COUNT));
      }
    }

    if (oidStr.startsWith(OID_IF_TABLE)) {
      String[] parts = oidStr.split("\\.");
      try {
        int col     = Integer.parseInt(parts[parts.length - 2]);
        int ifIndex = Integer.parseInt(parts[parts.length - 1]);
        if (ifIndex >= 1 && ifIndex <= IF_COUNT)
          return resolveIfEntry(oid, col, ifaces.get(ifIndex - 1));
      } catch (NumberFormatException ignored) {}
    }

    if (oidStr.startsWith(OID_IF_X_TABLE)) {
      String[] parts = oidStr.split("\\.");
      try {
        int col     = Integer.parseInt(parts[parts.length - 2]);
        int ifIndex = Integer.parseInt(parts[parts.length - 1]);
        if (ifIndex >= 1 && ifIndex <= IF_COUNT)
          return resolveIfXEntry(oid, col, ifaces.get(ifIndex - 1));
      } catch (NumberFormatException ignored) {}
    }

    return new VariableBinding(oid, Null.noSuchObject);
  }

  private VariableBinding resolveIfEntry(OID oid, int col, InterfaceData i) {
    return switch (col) {
      case 1  -> new VariableBinding(oid, new Integer32(i.ifIndex));
      case 2  -> new VariableBinding(oid, new OctetString(i.ifDescr));
      case 3  -> new VariableBinding(oid, new Integer32(i.ifType));
      case 4  -> new VariableBinding(oid, new Integer32(i.ifMtu));
      case 5  -> new VariableBinding(oid, new Gauge32(i.ifSpeed));
      case 6  -> new VariableBinding(oid, new OctetString(i.ifPhysAddress));
      case 7  -> new VariableBinding(oid, new Integer32(i.ifAdminStatus));
      case 8  -> new VariableBinding(oid, new Integer32(i.ifOperStatus));
      case 10 -> new VariableBinding(oid, new Counter32(i.ifInOctets.get()));
      case 11 -> new VariableBinding(oid, new Counter32(i.ifInUcastPkts.get()));
      case 13 -> new VariableBinding(oid, new Counter32(i.ifInDiscards.get()));
      case 14 -> new VariableBinding(oid, new Counter32(i.ifInErrors.get()));
      case 16 -> new VariableBinding(oid, new Counter32(i.ifOutOctets.get()));
      case 17 -> new VariableBinding(oid, new Counter32(i.ifOutUcastPkts.get()));
      case 19 -> new VariableBinding(oid, new Counter32(i.ifOutDiscards.get()));
      case 20 -> new VariableBinding(oid, new Counter32(i.ifOutErrors.get()));
      default -> new VariableBinding(oid, Null.noSuchObject);
    };
  }

  private VariableBinding resolveIfXEntry(OID oid, int col, InterfaceData i) {
    return switch (col) {
      case 1  -> new VariableBinding(oid, new OctetString(i.ifName));
      case 2  -> new VariableBinding(oid, new Counter32(i.ifInMulticastPkts.get()));
      case 3  -> new VariableBinding(oid, new Counter32(i.ifInBroadcastPkts.get()));
      case 4  -> new VariableBinding(oid, new Counter32(i.ifOutMulticastPkts.get()));
      case 5  -> new VariableBinding(oid, new Counter32(i.ifOutBroadcastPkts.get()));
      case 6  -> new VariableBinding(oid, new Counter64(i.ifHCInOctets.get()));
      case 7  -> new VariableBinding(oid, new Counter64(i.ifHCInUcastPkts.get()));
      case 8  -> new VariableBinding(oid, new Counter64(i.ifHCInMulticastPkts.get()));
      case 9  -> new VariableBinding(oid, new Counter64(i.ifHCInBroadcastPkts.get()));
      case 10 -> new VariableBinding(oid, new Counter64(i.ifHCOutOctets.get()));
      case 11 -> new VariableBinding(oid, new Counter64(i.ifHCOutUcastPkts.get()));
      case 12 -> new VariableBinding(oid, new Counter64(i.ifHCOutMulticastPkts.get()));
      case 13 -> new VariableBinding(oid, new Counter64(i.ifHCOutBroadcastPkts.get()));
      case 15 -> new VariableBinding(oid, new Gauge32(i.ifHighSpeed));
      case 18 -> new VariableBinding(oid, new OctetString(i.ifAlias));
      default -> new VariableBinding(oid, Null.noSuchObject);
    };
  }

  // ── Ticker ─────────────────────────────────────────────────────

  private void tickCounters() {
    agentInterfaces.values().forEach(ifaces ->
        ifaces.forEach(iface -> iface.tick(TICK_SECS)));
  }

  // ── Ciclo de vida ──────────────────────────────────────────────

  @PreDestroy
  public void stop() {
    ticker.shutdown();
    snmpInstances.forEach(snmp -> {
      try { snmp.close(); } catch (IOException ignored) {}
    });
    logger.info("Simulador detenido.");
  }

  public Map<Integer, List<InterfaceData>> getAgentInterfaces() {
    return agentInterfaces;
  }

  public List<Snmp> getSnmpInstances() {
    return snmpInstances;
  }
}
