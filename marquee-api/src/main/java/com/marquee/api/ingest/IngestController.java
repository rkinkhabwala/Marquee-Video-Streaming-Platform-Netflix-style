package com.marquee.api.ingest;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class IngestController {
    private final IngestService ingestService;

    public IngestController(IngestService ingestService) {
        this.ingestService = ingestService;
    }

    @PostMapping("/assets")
    public ResponseEntity<AssetUploadResponse> createAsset(@Valid @RequestBody CreateAssetRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ingestService.createAsset(request));
    }

    @GetMapping("/assets/{id}")
    public AssetStatusResponse getAsset(@PathVariable Long id) {
        return ingestService.getAsset(id);
    }

    @GetMapping("/titles/{titleId}/assets")
    public List<AssetStatusResponse> listAssets(@PathVariable Long titleId) {
        return ingestService.listAssetsForTitle(titleId);
    }

    @PostMapping("/assets/{id}/complete")
    public AssetStatusResponse completeAsset(@PathVariable Long id) {
        return ingestService.completeAsset(id);
    }
}
