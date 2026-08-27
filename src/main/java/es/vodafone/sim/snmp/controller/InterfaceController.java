package es.vodafone.sim.snmp.controller;

import es.vodafone.sim.snmp.model.InterfaceData;
import es.vodafone.sim.snmp.service.SnmpAgentSimulator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
  @Operation(summary = "Lista interfaces de un agente",
      parameters = @Parameter(name = "ip", description = "IP del agente con guiones", example = "127-1-0-1"),
      responses = {@ApiResponse(responseCode = "200", description = "OK"),
          @ApiResponse(responseCode = "404", description = "Agente no encontrado")})
  public List<InterfaceData> list(@PathVariable String ip) {
    return simulator.getInterfaces(normalize(ip));
  }

  @GetMapping("/{ifIdx}")
  @Operation(summary = "Detalle de una interfaz",
      parameters = {@Parameter(name = "ip", description = "IP del agente con guiones", example = "127-1-0-1"),
          @Parameter(name = "ifIdx", description = "Índice de interfaz (ifIndex)", example = "1")},
      responses = {@ApiResponse(responseCode = "200", description = "OK"),
          @ApiResponse(responseCode = "404", description = "Agente o interfaz no encontrada")})
  public InterfaceData get(@PathVariable String ip, @PathVariable int ifIdx) {
    return simulator.getInterfaces(normalize(ip)).stream()
        .filter(i -> i.ifIndex.equals(ifIdx))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("No existe ifIndex=" + ifIdx + " en " + normalize(ip)));
  }

  @PostMapping("/{ifIdx}")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Añade una interfaz",
      description = "Crea una nueva interfaz con el ifIndex dado en el agente.",
      parameters = {@Parameter(name = "ip", description = "IP del agente con guiones", example = "127-1-0-1"),
          @Parameter(name = "ifIdx", description = "Índice de la nueva interfaz", example = "11")},
      responses = {@ApiResponse(responseCode = "201", description = "Interfaz creada"),
          @ApiResponse(responseCode = "404", description = "Agente no encontrado"),
          @ApiResponse(responseCode = "409", description = "Ya existe una interfaz con ese índice")})
  public InterfaceData add(@PathVariable String ip, @PathVariable int ifIdx) {
    return simulator.addInterface(normalize(ip), ifIdx);
  }

  @PatchMapping("/{ifIdx}")
  @Operation(summary = "Modifica campos estáticos de una interfaz",
      description = "Actualización parcial (PATCH): solo se aplican los campos presentes en el body.",
      parameters = {@Parameter(name = "ip", description = "IP del agente con guiones", example = "127-1-0-1"),
          @Parameter(name = "ifIdx", description = "Índice de la interfaz a modificar", example = "1")},
      responses = {@ApiResponse(responseCode = "200", description = "Interfaz modificada"),
          @ApiResponse(responseCode = "404", description = "Agente o interfaz no encontrada")})
  public InterfaceData patch(@PathVariable String ip, @PathVariable int ifIdx,
                             @RequestBody InterfaceData patch) {
    return simulator.patchInterface(normalize(ip), ifIdx, patch);
  }

  @DeleteMapping("/{ifIdx}")
  @Operation(summary = "Elimina una interfaz",
      parameters = {@Parameter(name = "ip", description = "IP del agente con guiones", example = "127-1-0-1"),
          @Parameter(name = "ifIdx", description = "Índice de la interfaz a eliminar", example = "10")},
      responses = {@ApiResponse(responseCode = "200", description = "Interfaz eliminada"),
          @ApiResponse(responseCode = "404", description = "Agente o interfaz no encontrada")})
  public int remove(@PathVariable String ip, @PathVariable int ifIdx) {
    simulator.removeInterface(normalize(ip), ifIdx);
    return ifIdx;
  }
}