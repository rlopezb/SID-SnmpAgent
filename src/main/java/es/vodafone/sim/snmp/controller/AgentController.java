package es.vodafone.sim.snmp.controller;

import es.vodafone.sim.snmp.service.SnmpAgentSimulator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/agents")
public class AgentController {

  private final SnmpAgentSimulator simulator;

  public AgentController(SnmpAgentSimulator simulator) {
    this.simulator = simulator;
  }

  /** Lista todas las IPs de agentes activos. */
  @GetMapping
  public ResponseEntity<Map<String, Object>> list() {
    Set<String> ips = simulator.listAgents();
    return ResponseEntity.ok(Map.of(
        "count", ips.size(),
        "agents", ips
    ));
  }

  /** Añade un agente en la IP indicada. */
  @PostMapping("/{ip}")
  public ResponseEntity<Map<String, String>> add(@PathVariable String ip) {
    // PathVariable usa '.' como separador, codificar IPs con guiones: 127-1-0-1 → 127.1.0.1
    String normalizedIp = ip.replace('-', '.');
    try {
      simulator.addAgent(normalizedIp);
      return ResponseEntity.ok(Map.of(
          "status", "created",
          "ip", normalizedIp
      ));
    } catch (IllegalStateException e) {
      return ResponseEntity.status(409).body(Map.of(
          "error", e.getMessage()
      ));
    } catch (IOException e) {
      return ResponseEntity.status(500).body(Map.of(
          "error", "Error al arrancar el agente: " + e.getMessage()
      ));
    }
  }

  /** Elimina y cierra el agente en la IP indicada. */
  @DeleteMapping("/{ip}")
  public ResponseEntity<Map<String, String>> remove(@PathVariable String ip) {
    String normalizedIp = ip.replace('-', '.');
    try {
      simulator.removeAgent(normalizedIp);
      return ResponseEntity.ok(Map.of(
          "status", "deleted",
          "ip", normalizedIp
      ));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(404).body(Map.of(
          "error", e.getMessage()
      ));
    } catch (IOException e) {
      return ResponseEntity.status(500).body(Map.of(
          "error", "Error al cerrar el agente: " + e.getMessage()
      ));
    }
  }
}