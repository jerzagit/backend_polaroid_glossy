package com.polaroid.dto.request;

import lombok.Data;
import java.util.List;

@Data
public class StripDesignRequest {
    private Integer schemaVersion;
    private List<Asset> assets;
    private List<Design> designs;
    @Data public static class Asset { private String clientAssetId; }
    @Data public static class Design {
        private String clientDesignId;
        private Integer copies;
        private List<Slot> slots;
    }
    @Data public static class Slot {
        private Integer position;
        private String clientAssetId;
    }
}
