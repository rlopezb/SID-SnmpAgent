package es.vodafone.sim.snmp.model;

/**
 * DTO para PATCH parcial de una interfaz.
 * Todos los campos son nullable: solo se aplican los que vengan en el JSON.
 */
public record InterfacePatch(
    String  ifDescr,
    String  ifName,
    String  ifAlias,
    String  ifPhysAddress,
    Integer ifType,
    Integer ifMtu,
    Long    ifSpeed,       // bps; ifHighSpeed se recalcula automáticamente
    Integer ifAdminStatus,
    Integer ifOperStatus,
    Long    baseInBps,
    Long    baseOutBps,
    Boolean hasErrors
) {}