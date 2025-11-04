package net.thekingofduck.ningan.controller;

import net.thekingofduck.ningan.entity.HoneypotService;
import net.thekingofduck.ningan.service.HoneypotServiceRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/honeypot")
@CrossOrigin(origins = {"http://127.0.0.1:8080", "http://localhost:8080", "http://127.0.0.1:65535", "http://localhost:65535"})
public class HoneypotServiceController {

    @Autowired
    private HoneypotServiceRegistry registry;

    @GetMapping(value = "/services", produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    public ResponseEntity<?> listServices() {
        List<HoneypotService> list = registry.list();
        return ResponseEntity.ok(Map.of("success", true, "data", list));
    }

    @PostMapping(value = "/services", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    public ResponseEntity<?> addService(@RequestBody HoneypotService svc) {
        if (svc.getPort() == null || svc.getName() == null || svc.getName().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "缺少端口或服务名称"));
        }
        // 简单端口占用校验（仅校验注册表中是否重复）
        boolean occupied = registry.list().stream().anyMatch(s -> s.getPort() != null && s.getPort().equals(svc.getPort()));
        if (occupied) {
            return ResponseEntity.ok(Map.of("success", false, "message", "端口已被占用"));
        }
        HoneypotService created = registry.add(svc);
        return ResponseEntity.ok(Map.of("success", true, "data", created));
    }

    @PostMapping(value = "/services/{id}/start", produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    public ResponseEntity<?> start(@PathVariable("id") Long id) {
        boolean ok = registry.start(id);
        return ResponseEntity.ok(Map.of("success", ok));
    }

    @PostMapping(value = "/services/{id}/stop", produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    public ResponseEntity<?> stop(@PathVariable("id") Long id) {
        boolean ok = registry.stop(id);
        return ResponseEntity.ok(Map.of("success", ok));
    }

    @DeleteMapping(value = "/services/{id}", produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    public ResponseEntity<?> delete(@PathVariable("id") Long id) {
        boolean ok = registry.delete(id);
        return ResponseEntity.ok(Map.of("success", ok));
    }

    @PutMapping(value = "/services/{id}/config", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    public ResponseEntity<?> updateConfig(@PathVariable("id") Long id, @RequestBody Map<String, Object> changes) {
        HoneypotService svc = registry.update(id, changes);
        if (svc == null) return ResponseEntity.ok(Map.of("success", false, "message", "服务不存在"));
        return ResponseEntity.ok(Map.of("success", true, "data", svc));
    }

    @GetMapping(value = "/metrics", produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    public ResponseEntity<?> metrics() {
        return ResponseEntity.ok(Map.of("success", true, "data", registry.metrics()));
    }
}