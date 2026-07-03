package es.vodafone.sim.snmp.model;

import java.util.concurrent.atomic.AtomicLong;
import java.util.Random;

public class InterfaceData {

  private static final Random RNG = new Random();

  // ── Campos estáticos mutables ──────────────────────────────────
  public volatile int ifIndex;
  public volatile String ifDescr;
  public volatile String ifName;
  public volatile String ifAlias;
  public volatile String ifPhysAddress;
  public volatile int ifType = 6;            // ethernetCsmacd
  public volatile int ifMtu = 9000;
  public volatile long ifSpeed = 1_000_000_000L; // 1 Gbps
  public volatile int ifHighSpeed = 1000;          // Mbps
  public volatile int ifAdminStatus = 1;           // up
  public volatile int ifOperStatus;

  // ── Contadores 32-bit (ifTable) ───────────────────────────────
  public final AtomicLong ifInOctets = new AtomicLong();
  public final AtomicLong ifInUcastPkts = new AtomicLong();
  public final AtomicLong ifInDiscards = new AtomicLong();
  public final AtomicLong ifInErrors = new AtomicLong();
  public final AtomicLong ifOutOctets = new AtomicLong();
  public final AtomicLong ifOutUcastPkts = new AtomicLong();
  public final AtomicLong ifOutDiscards = new AtomicLong();
  public final AtomicLong ifOutErrors = new AtomicLong();

  // ── Contadores 64-bit HC (ifXTable) ──────────────────────────
  public final AtomicLong ifHCInOctets = new AtomicLong();
  public final AtomicLong ifHCInUcastPkts = new AtomicLong();
  public final AtomicLong ifHCInMulticastPkts = new AtomicLong();
  public final AtomicLong ifHCInBroadcastPkts = new AtomicLong();
  public final AtomicLong ifHCOutOctets = new AtomicLong();
  public final AtomicLong ifHCOutUcastPkts = new AtomicLong();
  public final AtomicLong ifHCOutMulticastPkts = new AtomicLong();
  public final AtomicLong ifHCOutBroadcastPkts = new AtomicLong();

  // ── Multicast/broadcast 32-bit ────────────────────────────────
  public final AtomicLong ifInMulticastPkts = new AtomicLong();
  public final AtomicLong ifInBroadcastPkts = new AtomicLong();
  public final AtomicLong ifOutMulticastPkts = new AtomicLong();
  public final AtomicLong ifOutBroadcastPkts = new AtomicLong();

  // ── Tráfico base interno ──────────────────────────────────────
  public volatile long baseInBps;
  public volatile long baseOutBps;
  public volatile boolean hasErrors;

  private static final String[] DESCRIPTIONS = {"RSR-PTN", "RSR-CPN", "RSR-TSR"};

  // ── Constructor ───────────────────────────────────────────────

  public InterfaceData(int agentIndex, int ifIdx) {
    this.ifIndex = ifIdx;
    this.ifDescr = "GigabitEthernet0/" + (ifIdx - 1);
    this.ifName = "Gi0/" + (ifIdx - 1);
    this.ifAlias = "RSR" + String.format("%03d", agentIndex) + " " + DESCRIPTIONS[ifIdx % DESCRIPTIONS.length];
    this.ifPhysAddress = String.format("%02x:%02x:%02x:%02x:%02x:%02x",
        0x02, agentIndex >> 8 & 0xFF, agentIndex & 0xFF, 0x00, ifIdx, 0x01);
    this.ifOperStatus = (ifIdx <= 8) ? 1 : 2;
    this.baseInBps = (long) (RNG.nextDouble() * 112_500_000) + 125_000;
    this.baseOutBps = (long) (RNG.nextDouble() * 56_250_000) + 62_500;
    this.hasErrors = RNG.nextInt(10) == 0;
  }

  // ── Patch parcial ─────────────────────────────────────────────

  /**
   * Aplica solo los campos no nulos del patch.
   * Devuelve this para permitir encadenamiento.
   */
  public InterfaceData applyPatch(InterfacePatch p) {
    if (p.ifDescr() != null) this.ifDescr = p.ifDescr();
    if (p.ifName() != null) this.ifName = p.ifName();
    if (p.ifAlias() != null) this.ifAlias = p.ifAlias();
    if (p.ifPhysAddress() != null) this.ifPhysAddress = p.ifPhysAddress();
    if (p.ifType() != null) this.ifType = p.ifType();
    if (p.ifMtu() != null) this.ifMtu = p.ifMtu();
    if (p.ifSpeed() != null) {
      this.ifSpeed = p.ifSpeed();
      this.ifHighSpeed = (int) (p.ifSpeed() / 1_000_000);
    }
    if (p.ifAdminStatus() != null) this.ifAdminStatus = p.ifAdminStatus();
    if (p.ifOperStatus() != null) this.ifOperStatus = p.ifOperStatus();
    if (p.baseInBps() != null) this.baseInBps = p.baseInBps();
    if (p.baseOutBps() != null) this.baseOutBps = p.baseOutBps();
    if (p.hasErrors() != null) this.hasErrors = p.hasErrors();
    return this;
  }

  // ── Ticker ────────────────────────────────────────────────────

  public void tick(int intervalSeconds) {
    if (ifOperStatus != 1) return;

    double jitter = 0.8 + RNG.nextDouble() * 0.4;
    long inBytes = (long) (baseInBps * intervalSeconds * jitter);
    long outBytes = (long) (baseOutBps * intervalSeconds * jitter);
    long inPkts = inBytes / 1400;
    long outPkts = outBytes / 1400;
    long inMcast = inPkts / 100;
    long outMcast = outPkts / 100;
    long inBcast = inPkts / 500;
    long outBcast = outPkts / 500;

    ifInOctets.addAndGet(inBytes);
    ifInUcastPkts.addAndGet(inPkts);
    ifOutOctets.addAndGet(outBytes);
    ifOutUcastPkts.addAndGet(outPkts);
    ifInMulticastPkts.addAndGet(inMcast);
    ifOutMulticastPkts.addAndGet(outMcast);
    ifInBroadcastPkts.addAndGet(inBcast);
    ifOutBroadcastPkts.addAndGet(outBcast);

    ifHCInOctets.addAndGet(inBytes);
    ifHCInUcastPkts.addAndGet(inPkts);
    ifHCInMulticastPkts.addAndGet(inMcast);
    ifHCInBroadcastPkts.addAndGet(inBcast);
    ifHCOutOctets.addAndGet(outBytes);
    ifHCOutUcastPkts.addAndGet(outPkts);
    ifHCOutMulticastPkts.addAndGet(outMcast);
    ifHCOutBroadcastPkts.addAndGet(outBcast);

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