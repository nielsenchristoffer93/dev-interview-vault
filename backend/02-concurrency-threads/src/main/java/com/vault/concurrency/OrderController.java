package com.vault.concurrency;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final UnsafeOrderService unsafeService;
    private final SafeOrderService safeService;

    public OrderController(UnsafeOrderService unsafeService, SafeOrderService safeService) {
        this.unsafeService = unsafeService;
        this.safeService = safeService;
    }

    // THE FLAW: Oskyddad trådosäker räknare (Lost Updates)
    @GetMapping("/unsafe/increment")
    public String incrementUnsafe() {
        unsafeService.createOrder();
        return "UNSAFE_OK";
    }

    @GetMapping("/unsafe/count")
    public Map<String, Object> getUnsafeCount() {
        return Map.of(
                "type", "UNSAFE (Primitive int with Race Condition)",
                "count", unsafeService.getOrderCount()
        );
    }

    // THE FIX: Atomär trådsäker räknare (AtomicLong med CAS)
    @GetMapping("/safe/increment")
    public String incrementSafe() {
        safeService.createOrder();
        return "SAFE_OK";
    }

    @GetMapping("/safe/count")
    public Map<String, Object> getSafeCount() {
        return Map.of(
                "type", "SAFE (AtomicLong with CAS)",
                "count", safeService.getOrderCount()
        );
    }

    // Nollställ för demo-körningar
    @PostMapping("/reset")
    public Map<String, String> reset() {
        unsafeService.reset();
        safeService.reset();
        return Map.of("status", "RESET_OK");
    }
}
