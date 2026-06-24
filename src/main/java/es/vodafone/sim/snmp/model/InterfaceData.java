package es.vodafone.sim.snmp.model;

import java.util.concurrent.atomic.AtomicLong;
import java.util.Random;

public class InterfaceData {

  private static final Random RNG = new Random();

  // Estáticos
  public final int    ifIndex;
  public final String ifDescr;
  public final String ifName;
  public final String ifAlias;
  public final String ifPhysAddress;
  public final int    ifType   = 6;       // ethernetCsmacd
  public final int    ifMtu    = 9000;
  public final long   ifSpeed  = 1_000_000_000L;  // 1 Gbps
  public final int    ifHighSpeed = 1000;          // Mbps
  public final int    ifAdminStatus = 1;  // up
  public final int    ifOperStatus;

  // Contadores 32-bit (ifTable)
  public final AtomicLong ifInOctets      = new AtomicLong();
  public final AtomicLong ifInUcastPkts   = new AtomicLong();
  public final AtomicLong ifInDiscards    = new AtomicLong();
  public final AtomicLong ifInErrors      = new AtomicLong();
  public final AtomicLong ifOutOctets     = new AtomicLong();
  public final AtomicLong ifOutUcastPkts  = new AtomicLong();
  public final AtomicLong ifOutDiscards   = new AtomicLong();
  public final AtomicLong ifOutErrors     = new AtomicLong();

  // Contadores 64-bit HC (ifXTable)
  public final AtomicLong ifHCInOctets          = new AtomicLong();
  public final AtomicLong ifHCInUcastPkts        = new AtomicLong();
  public final AtomicLong ifHCInMulticastPkts    = new AtomicLong();
  public final AtomicLong ifHCInBroadcastPkts    = new AtomicLong();
  public final AtomicLong ifHCOutOctets          = new AtomicLong();
  public final AtomicLong ifHCOutUcastPkts       = new AtomicLong();
  public final AtomicLong ifHCOutMulticastPkts   = new AtomicLong();
  public final AtomicLong ifHCOutBroadcastPkts   = new AtomicLong();

  // Multicast/broadcast 32-bit
  public final AtomicLong ifInMulticastPkts   = new AtomicLong();
  public final AtomicLong ifInBroadcastPkts   = new AtomicLong();
  public final AtomicLong ifOutMulticastPkts  = new AtomicLong();
  public final AtomicLong ifOutBroadcastPkts  = new AtomicLong();

  // Tasa de tráfico base (bytes/s) — varía por interfaz
  private final long baseInBps;
  private final long baseOutBps;
  private final boolean hasErrors;

  private static final String[] DESCRIPTIONS = {
      "RSR-PTN",
      "RSR-CPN",
      "RSR-TSR"
  };
  public InterfaceData(int agentIndex, int ifIdx) {
    this.ifIndex = ifIdx;
    this.ifDescr = "GigabitEthernet0/" + (ifIdx - 1);
    this.ifName  = "Gi0/" + (ifIdx - 1);
    this.ifAlias = "RSR" + String.format("%03d", agentIndex) + " "  + DESCRIPTIONS[ifIdx % DESCRIPTIONS.length];
    this.ifPhysAddress = String.format("%02x:%02x:%02x:%02x:%02x:%02x",
        0x02, agentIndex >> 8 & 0xFF, agentIndex & 0xFF,
        0x00, ifIdx, 0x01);

    // Algunas interfaces down para realismo
    this.ifOperStatus = (ifIdx <= 8) ? 1 : 2;  // if 9,10 → down

    // Tráfico base aleatorio entre 1 Mbps y 900 Mbps
    this.baseInBps  = (long)(RNG.nextDouble() * 112_500_000) + 125_000;
    this.baseOutBps = (long)(RNG.nextDouble() *  56_250_000) + 62_500;

    // 10% de interfaces con errores ocasionales
    this.hasErrors = RNG.nextInt(10) == 0;
  }

  /** Llamado cada `intervalSeconds` por el ticker */
  public void tick(int intervalSeconds) {
    if (ifOperStatus != 1) return;  // interfaz down, sin tráfico

    // Variación ±20% sobre base
    double jitter = 0.8 + RNG.nextDouble() * 0.4;
    long inBytes  = (long)(baseInBps  * intervalSeconds * jitter);
    long outBytes = (long)(baseOutBps * intervalSeconds * jitter);

    long inPkts   = inBytes  / 1400;
    long outPkts  = outBytes / 1400;
    long inMcast  = inPkts  / 100;
    long outMcast = outPkts / 100;
    long inBcast  = inPkts  / 500;
    long outBcast = outPkts / 500;

    // 32-bit (wrap natural de AtomicLong truncado a int en la respuesta)
    ifInOctets.addAndGet(inBytes);
    ifInUcastPkts.addAndGet(inPkts);
    ifOutOctets.addAndGet(outBytes);
    ifOutUcastPkts.addAndGet(outPkts);
    ifInMulticastPkts.addAndGet(inMcast);
    ifOutMulticastPkts.addAndGet(outMcast);
    ifInBroadcastPkts.addAndGet(inBcast);
    ifOutBroadcastPkts.addAndGet(outBcast);

    // HC 64-bit (mismos valores, sin truncar)
    ifHCInOctets.addAndGet(inBytes);
    ifHCInUcastPkts.addAndGet(inPkts);
    ifHCInMulticastPkts.addAndGet(inMcast);
    ifHCInBroadcastPkts.addAndGet(inBcast);
    ifHCOutOctets.addAndGet(outBytes);
    ifHCOutUcastPkts.addAndGet(outPkts);
    ifHCOutMulticastPkts.addAndGet(outMcast);
    ifHCOutBroadcastPkts.addAndGet(outBcast);

    // Errores y discards ocasionales
    if (hasErrors && RNG.nextInt(5) == 0) {
      ifInErrors.addAndGet(RNG.nextInt(3));
      ifOutErrors.addAndGet(RNG.nextInt(2));
    }
    if (RNG.nextInt(20) == 0) {
      ifInDiscards.addAndGet(RNG.nextInt(5));
      ifOutDiscards.addAndGet(RNG.nextInt(3));
    }
  }
}