package es.vodafone.sim.snmp.controller;

import es.vodafone.sim.snmp.model.InterfaceData;
import es.vodafone.sim.snmp.model.InterfacePatch;
import es.vodafone.sim.snmp.service.SnmpAgentSimulator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/agents/{ip}/interfaces")
@Tag(name = "Interfaces", description = "Gestión dinámica de interfaces por agente")
public class InterfaceController {

  private final SnmpAgentSimulator simulator;

  public InterfaceController(SnmpAgentSimulator simulator) {
    this.simulator = simulator;
  }

  private String normalize(String ip) { return ip.replace('-', '.'); }

  @GetMapping
  @Operation(
      summary = "Lista interfaces de un agente",
      parameters = @Parameter(name = "ip", description = "IP del agente con guiones", example = "127-1-0-1"),
      responses = {
          @ApiResponse(responseCode = "200", description = "OK"),
          @ApiResponse(responseCode = "404", description = "Agente no encontrado")
      }
  )
  public ResponseEntity<?> list(@PathVariable String ip) {
    try {
      List<Map<String, Object>> result = simulator.getInterfaces(normalize(ip))
          .stream().map(this::toMap).toList();
      return ResponseEntity.ok(Map.of("count", result.size(), "interfaces", result));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
    }
  }

  @GetMapping("/{ifIdx}")
  @Operation(
      summary = "Detalle de una interfaz",
      parameters = {
          @Parameter(name = "ip",    description = "IP del agente con guiones", example = "127-1-0-1"),
          @Parameter(name = "ifIdx", description = "Índice de interfaz (ifIndex)", example = "1")
      },
      responses = {
          @ApiResponse(responseCode = "200", description = "OK"),
          @ApiResponse(responseCode = "404", description = "Agente o interfaz no encontrada")
      }
  )
  public ResponseEntity<?> get(@PathVariable String ip, @PathVariable int ifIdx) {
    try {
      return simulator.getInterfaces(normalize(ip)).stream()
          .filter(i -> i.ifIndex == ifIdx)
          .findFirst()
          .map(i -> ResponseEntity.ok((Object) toMap(i)))
          .orElse(ResponseEntity.status(404).body(
              Map.of("error", "No existe ifIndex=" + ifIdx + " en " + normalize(ip))));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
    }
  }

  @PostMapping("/{ifIdx}")
  @Operation(
      summary = "Añade una interfaz",
      description = "Crea una nueva interfaz con el ifIndex dado en el agente. Se inicializa con valores por defecto y tráfico aleatorio.",
      parameters = {
          @Parameter(name = "ip",    description = "IP del agente con guiones", example = "127-1-0-1"),
          @Parameter(name = "ifIdx", description = "Índice de la nueva interfaz", example = "11")
      },
      responses = {
          @ApiResponse(responseCode = "201", description = "Interfaz creada"),
          @ApiResponse(responseCode = "404", description = "Agente no encontrado"),
          @ApiResponse(responseCode = "409", description = "Ya existe una interfaz con ese índice")
      }
  )
  public ResponseEntity<?> add(@PathVariable String ip, @PathVariable int ifIdx) {
    try {
      InterfaceData iface = simulator.addInterface(normalize(ip), ifIdx);
      return ResponseEntity.status(201).body(toMap(iface));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
    } catch (IllegalStateException e) {
      return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
    }
  }

  @PatchMapping("/{ifIdx}")
  @Operation(
      summary = "Modifica campos estáticos de una interfaz",
      description = """
          Actualización parcial (PATCH): solo se aplican los campos presentes en el body.
          Los contadores no se ven afectados.
          Si se cambia `ifSpeed` (bps), `ifHighSpeed` (Mbps) se recalcula automáticamente.
          """,
      parameters = {
          @Parameter(name = "ip",    description = "IP del agente con guiones", example = "127-1-0-1"),
          @Parameter(name = "ifIdx", description = "Índice de la interfaz a modificar", example = "1")
      },
      responses = {
          @ApiResponse(responseCode = "200", description = "Interfaz modificada"),
          @ApiResponse(responseCode = "404", description = "Agente o interfaz no encontrada")
      }
  )
  public ResponseEntity<?> patch(@PathVariable String ip, @PathVariable int ifIdx,
                                 @RequestBody InterfacePatch patch) {
    try {
      InterfaceData iface = simulator.patchInterface(normalize(ip), ifIdx, patch);
      return ResponseEntity.ok(toMap(iface));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
    }
  }

  @DeleteMapping("/{ifIdx}")
  @Operation(
      summary = "Elimina una interfaz",
      parameters = {
          @Parameter(name = "ip",    description = "IP del agente con guiones", example = "127-1-0-1"),
          @Parameter(name = "ifIdx", description = "Índice de la interfaz a eliminar", example = "10")
      },
      responses = {
          @ApiResponse(responseCode = "200", description = "Interfaz eliminada"),
          @ApiResponse(responseCode = "404", description = "Agente o interfaz no encontrada")
      }
  )
  public ResponseEntity<?> remove(@PathVariable String ip, @PathVariable int ifIdx) {
    try {
      simulator.removeInterface(normalize(ip), ifIdx);
      return ResponseEntity.ok(Map.of("status", "deleted", "ifIdx", ifIdx));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
    }
  }

  // ── Serialización completa ─────────────────────────────────────

  private Map<String, Object> toMap(InterfaceData i) {
    Map<String, Object> m = new LinkedHashMap<>();

    // Campos estáticos
    m.put("ifIndex",       i.ifIndex);
    m.put("ifDescr",       i.ifDescr);
    m.put("ifName",        i.ifName);
    m.put("ifAlias",       i.ifAlias);
    m.put("ifPhysAddress", i.ifPhysAddress);
    m.put("ifType",        i.ifType);
    m.put("ifMtu",         i.ifMtu);
    m.put("ifSpeed",       i.ifSpeed);
    m.put("ifHighSpeed",   i.ifHighSpeed);
    m.put("ifAdminStatus", i.ifAdminStatus);
    m.put("ifOperStatus",  i.ifOperStatus);

    // Parámetros de simulación
    m.put("baseInBps",     i.baseInBps);
    m.put("baseOutBps",    i.baseOutBps);
    m.put("hasErrors",     i.hasErrors);

    // Contadores 32-bit (ifTable)
    m.put("ifInOctets",     i.ifInOctets.get());
    m.put("ifInUcastPkts",  i.ifInUcastPkts.get());
    m.put("ifInDiscards",   i.ifInDiscards.get());
    m.put("ifInErrors",     i.ifInErrors.get());
    m.put("ifOutOctets",    i.ifOutOctets.get());
    m.put("ifOutUcastPkts", i.ifOutUcastPkts.get());
    m.put("ifOutDiscards",  i.ifOutDiscards.get());
    m.put("ifOutErrors",    i.ifOutErrors.get());

    // Multicast / broadcast 32-bit
    m.put("ifInMulticastPkts",  i.ifInMulticastPkts.get());
    m.put("ifInBroadcastPkts",  i.ifInBroadcastPkts.get());
    m.put("ifOutMulticastPkts", i.ifOutMulticastPkts.get());
    m.put("ifOutBroadcastPkts", i.ifOutBroadcastPkts.get());

    // Contadores HC 64-bit (ifXTable)
    m.put("ifHCInOctets",         i.ifHCInOctets.get());
    m.put("ifHCInUcastPkts",      i.ifHCInUcastPkts.get());
    m.put("ifHCInMulticastPkts",  i.ifHCInMulticastPkts.get());
    m.put("ifHCInBroadcastPkts",  i.ifHCInBroadcastPkts.get());
    m.put("ifHCOutOctets",        i.ifHCOutOctets.get());
    m.put("ifHCOutUcastPkts",     i.ifHCOutUcastPkts.get());
    m.put("ifHCOutMulticastPkts", i.ifHCOutMulticastPkts.get());
    m.put("ifHCOutBroadcastPkts", i.ifHCOutBroadcastPkts.get());

    return m;
  }
}