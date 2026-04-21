/**
 * Legacy hook: "Manage mappings" modal was removed. The booking page gear button now opens
 * Consignee Map in a new tab (see CarBooking.kt). Kept as a no-op so older bookmarks do not throw.
 */
window.showBookingMappingsModal = function (_country) {
  /* no-op */
};
