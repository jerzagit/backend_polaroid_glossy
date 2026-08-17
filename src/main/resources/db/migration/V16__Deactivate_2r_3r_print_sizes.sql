-- Decommission the legacy 2R and 3R print sizes.
-- Soft-delete (is_active = false) so historical order_items keep their FK valid.
UPDATE print_sizes SET is_active = false WHERE id IN ('2r', '3r');