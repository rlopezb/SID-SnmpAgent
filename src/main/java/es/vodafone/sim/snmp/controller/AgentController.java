package es.vodafone.sim.snmp.controller;

import es.vodafone.sim.snmp.service.SnmpAgentSimulator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/agents")
@Tag(name = "Agents", description = "Gestión dinámica de agentes SNMPv3")
public class AgentController {

  private final SnmpAgentSimulator simulator;

  public AgentController(SnmpAgentSimulator simulator) {
    this.simulator = simulator;
  }

  private String normalize(String ip) { return ip.replace('-', '.'); }

  @GetMapping
  @Operation(
      summary = "Lista agentes activos",
      description = "Devuelve las IPs de todos los agentes SNMPv3 activos y su número total.",
      responses = {
          @ApiResponse(responseCode = "200", description = "OK")
      }
  )
  public ResponseEntity<Map<String, Object>> list() {
    Set<String> ips = simulator.listAgents();
    return ResponseEntity.ok(Map.of("count", ips.size(), "agents", ips));
  }

  @PostMapping("/{ip}")
  @Operation(
      summary = "Añade un agente",
      description = """
          Arranca un nuevo agente SNMPv3 en la IP indicada (puerto 1161).
          La IP se pasa con guiones en lugar de puntos: `127-1-2-5` → `127.1.2.5`.
          Se crean 10 interfaces por defecto con tráfico simulado aleatorio.
          """,
      parameters = @Parameter(name = "ip", description = "IP del agente con guiones (ej. 127-1-2-5)", example = "127-1-2-5"),
      responses = {
          @ApiResponse(responseCode = "200", description = "Agente creado"),
          @ApiResponse(responseCode = "409", description = "Ya existe un agente en esa IP"),
          @ApiResponse(responseCode = "500", description = "Error al arrancar el socket UDP")
      }
  )
  public ResponseEntity<Map<String, String>> add(@PathVariable String ip) {
    String normalizedIp = normalize(ip);
    try {
      simulator.addAgent(normalizedIp);
      return ResponseEntity.ok(Map.of("status", "created", "ip", normalizedIp));
    } catch (IllegalStateException e) {
      return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
    } catch (IOException e) {
      return ResponseEntity.status(500).body(Map.of("error", "Error al arrancar el agente: " + e.getMessage()));
    }
  }

  @DeleteMapping("/{ip}")
  @Operation(
      summary = "Elimina un agente",
      description = "Detiene y cierra el agente en la IP indicada, liberando el socket UDP.",
      parameters = @Parameter(name = "ip", description = "IP del agente con guiones (ej. 127-1-2-5)", example = "127-1-2-5"),
      responses = {
          @ApiResponse(responseCode = "200", description = "Agente eliminado"),
          @ApiResponse(responseCode = "404", description = "No existe ningún agente en esa IP"),
          @ApiResponse(responseCode = "500", description = "Error al cerrar el socket UDP")
      }
  )
  public ResponseEntity<Map<String, String>> remove(@PathVariable String ip) {
    String normalizedIp = normalize(ip);
    try {
      simulator.removeAgent(normalizedIp);
      return ResponseEntity.ok(Map.of("status", "deleted", "ip", normalizedIp));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
    } catch (IOException e) {
      return ResponseEntity.status(500).body(Map.of("error", "Error al cerrar el agente: " + e.getMessage()));
    }
  }
}