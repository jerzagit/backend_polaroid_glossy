package com.polaroid.model;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

/** Versioned, server-owned document. Never deserialize this type from a customer request. */
@Data
public class StripDesignState {
    private int schemaVersion = 1;
    private int slotCount;
    private List<Asset> assets = new ArrayList<>();
    private List<Design> designs = new ArrayList<>();
    @Data public static class Asset {
        private String id;
        private String clientAssetId;
        private String status = "pending";
        private String storageKey;
        private String sha256;
        private String mimeType;
        private Integer width;
        private Integer height;
        private String orientationNormalization = "not-applied-source-preserved";
        private String originalFilename;
    }
    @Data public static class Design {
        private String id;
        private String clientDesignId;
        private String label;
        private int revision = 1;
        private int copies;
        private List<Slot> slots = new ArrayList<>();
    }
    @Data public static class Slot {
        private int position;
        private String assetId;
    }
}
