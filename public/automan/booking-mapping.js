// Booking mappings: auto-fill POD and CONSIGNEE based on country selection

// API Base URL - use relative path so nginx can proxy to backend (only declare if not already declared)
if (typeof window.API_BASE_URL === 'undefined') {
    window.API_BASE_URL = '/api';
}

// Helper function to get API URL (only declare if not already declared)
if (typeof window.apiUrl === 'undefined') {
    window.apiUrl = function(path) {
        const cleanPath = path.startsWith('/') ? path.substring(1) : path;
        return `${window.API_BASE_URL}/${cleanPath}`;
    };
}

// Cache for booking mappings
window.bookingMappingsCache = {};

/**
 * Fetch booking mappings from API by country
 */
window.fetchBookingMappingsByCountry = async function(country) {
  if (!country) return [];
  
  // Check cache first
  if (window.bookingMappingsCache[country]) {
    return window.bookingMappingsCache[country];
  }
  
  try {
    const response = await fetch(window.apiUrl(`booking/mappings/by-country/${encodeURIComponent(country)}`));
    const result = await response.json();
    const mappings = result.success ? result.data : [];
    
    // Fallback: in case `booking_mappings.country` is stored as a comma/semicolon-separated list
    // (e.g. "DURBAN, UGANDA, KENYA, MAPUTO"), the backend by-country endpoint may return [].
    if (mappings.length === 0) {
      const normalizedWanted = (country || '').trim().toLowerCase();
      if (normalizedWanted) {
        const allResp = await fetch(window.apiUrl('booking/mappings'));
        const allResult = await allResp.json();
        if (allResult.success && Array.isArray(allResult.data)) {
          const wantedMappings = allResult.data.filter(m => {
            const rawCountry = (m && m.country) ? m.country : '';
            const tokens = rawCountry
              .toString()
              .split(/[;,]/)
              .map(s => s.trim())
              .filter(Boolean);
            return tokens.some(t => t.toLowerCase() === normalizedWanted);
          });
          window.bookingMappingsCache[country] = wantedMappings;
          return wantedMappings;
        }
      }
    }

    // Cache the results (including the non-empty happy path)
    window.bookingMappingsCache[country] = mappings;
    return mappings;
  } catch (error) {
    console.error('Error fetching booking mappings:', error);
    return [];
  }
};

/**
 * Get unique POD values from mappings
 */
function getUniquePODs(mappings) {
  const pods = mappings
    .flatMap(m => {
      const raw = (m.pod || '').toString();
      // Support comma-separated or semicolon-separated pod lists.
      return raw.split(/[;,]/).map(s => s.trim()).filter(Boolean);
    });
  
  // Remove duplicates
  return [...new Set(pods)];
}

/**
 * Get unique consignee combinations from mappings
 */
function getUniqueConsignees(mappings) {
  const consignees = [];
  const seen = new Set();
  
  mappings.forEach(m => {
    if (!m.consigneeName && !m.consigneeAddress) return;
    
    const key = `${m.consigneeName || ''}|${m.consigneeAddress || ''}`;
    if (!seen.has(key)) {
      seen.add(key);
      consignees.push({
        name: m.consigneeName || '',
        address: m.consigneeAddress || ''
      });
    }
  });
  
  return consignees;
}

/**
 * Rows whose consignee_name matches (trim, case-insensitive) — used so POD list matches the selected consignee only.
 */
function filterMappingsByConsigneeName(mappings, name) {
  const n = (name || '').trim().toLowerCase();
  if (!n || !Array.isArray(mappings)) return [];
  return mappings.filter(function(m) {
    var cn = (m && m.consigneeName) ? String(m.consigneeName).trim().toLowerCase() : '';
    return cn === n;
  });
}

/**
 * Repopulate POD options from the mapping row(s) for the current consignee dropdown selection.
 * @param {boolean} preserveCurrentPod — if false (e.g. user changed consignee), do not keep old POD / add custom option; pick first POD of this consignee.
 */
function applyConsigneePodRefresh(preserveCurrentPod) {
  var mappings = window.__carBookingMappingsByCountry || [];
  var consigneeSelect = document.getElementById('consigneeSelect');
  var sub = mappings;
  if (consigneeSelect && consigneeSelect.selectedIndex > 0) {
    var sel = consigneeSelect.options[consigneeSelect.selectedIndex];
    var name = (sel && (sel.textContent || sel.value)) ? String(sel.textContent || '').trim() : '';
    sub = filterMappingsByConsigneeName(mappings, name);
  } else if (consigneeSelect) {
    sub = [];
  }
  populatePODDropdown(sub, preserveCurrentPod !== false);
}

/**
 * Populate POD dropdown with unique POD values from the given mapping rows only.
 * @param {Array} mappings — usually filtered to one consignee so PODs match Consignee Map / booking_mappings.
 * @param {boolean} [preserveCurrentPod=true] — when false, clear current POD before filling (consignee switch).
 */
function populatePODDropdown(mappings, preserveCurrentPod) {
  const podInput = document.getElementById('podPort');
  if (!podInput) return;
  
  const pods = getUniquePODs(mappings);
  
  // Preserve current value before converting/updating (unless switching consignee)
  const currentValue = (preserveCurrentPod !== false && podInput.value) ? podInput.value.trim() : '';
  
  // Convert input to select if it's not already
  let podSelect = podInput;
  if (podInput.tagName !== 'SELECT') {
    // Create select element
    const select = document.createElement('select');
    select.id = 'podPort';
    select.name = podInput.name || 'podPort';
    select.style.cssText = podInput.style.cssText;
    select.className = podInput.className;
    select.setAttribute('placeholder', podInput.getAttribute('placeholder') || 'PORT OF DISCHARGE');
    
    // Copy all attributes except type and value
    Array.from(podInput.attributes).forEach(attr => {
      if (attr.name !== 'type' && attr.name !== 'value') {
        select.setAttribute(attr.name, attr.value);
      }
    });
    
    // Replace input with select
    podInput.parentNode.replaceChild(select, podSelect);
    podSelect = select;
  }
  
  // Clear existing options
  podSelect.innerHTML = '<option value="">Select POD</option>';
  
  // Add POD options
  pods.forEach(pod => {
    const option = document.createElement('option');
    option.value = pod;
    option.textContent = pod;
    podSelect.appendChild(option);
  });
  
  // Restore preserved value if it exists and is in the options, otherwise auto-select first POD only if no value
  if (currentValue && currentValue !== '') {
    // Check if current value exists in options
    const valueExists = Array.from(podSelect.options).some(opt => opt.value === currentValue);
    if (valueExists) {
      podSelect.value = currentValue;
      console.log('✅ Preserved POD value:', currentValue);
    } else {
      // If value doesn't exist in options, add it as a custom option
      const customOption = document.createElement('option');
      customOption.value = currentValue;
      customOption.textContent = currentValue;
      podSelect.appendChild(customOption);
      podSelect.value = currentValue;
      console.log('✅ Preserved POD value (custom):', currentValue);
    }
    // Ensure listeners (LIST refresh, etc.) run after we set the value.
    podSelect.dispatchEvent(new Event('change', { bubbles: true }));
  } else if (pods.length > 0 && !podSelect.value) {
    // Only auto-select first POD if no value was preserved
    podSelect.value = pods[0];
    // Trigger change event for any listeners
    podSelect.dispatchEvent(new Event('change', { bubbles: true }));
  }

  if (typeof window.ensureBookingFabPod === 'function') {
    window.ensureBookingFabPod();
  }
}

/**
 * Populate CONSIGNEE field and dropdown (name only — address is not shown in the UI)
 */
function populateConsigneeField(mappings) {
  const consigneeInput = document.getElementById('consigneeName');
  if (!consigneeInput) return;
  
  const consignees = getUniqueConsignees(mappings);
  
  // Create dropdown container if it doesn't exist
  let consigneeContainer = document.getElementById('consigneeContainer');
  let consigneeSelect = document.getElementById('consigneeSelect');
  
  if (!consigneeContainer) {
    // Create container div
    consigneeContainer = document.createElement('div');
    consigneeContainer.id = 'consigneeContainer';
    consigneeContainer.style.cssText = 'position: relative; width: 100%; margin-top: 8px;';
    
    consigneeSelect = document.createElement('select');
    consigneeSelect.id = 'consigneeSelect';
    consigneeSelect.className = 'rixo-company-fab-native-select';
    consigneeSelect.setAttribute('tabindex', '-1');
    consigneeSelect.setAttribute('aria-hidden', 'true');

    var fabWrap = document.createElement('div');
    fabWrap.className = 'rixo-company-fab-wrap booking-fab-field';
    fabWrap.id = 'bookingConsigneeFabWrap';
    var fabCol = document.createElement('div');
    fabCol.className = 'rixo-company-fab';
    var fabTrigger = document.createElement('button');
    fabTrigger.type = 'button';
    fabTrigger.className = 'rixo-fab-trigger';
    fabTrigger.id = 'bookingConsigneeFabTrigger';
    fabTrigger.setAttribute('aria-expanded', 'false');
    fabTrigger.setAttribute('aria-haspopup', 'listbox');
    fabTrigger.setAttribute('aria-controls', 'bookingConsigneeFabActions');
    var fabTw = document.createElement('span');
    fabTw.className = 'rixo-fab-trigger-text-wrap';
    var fabLab = document.createElement('span');
    fabLab.className = 'rixo-fab-trigger-label';
    fabLab.id = 'bookingConsigneeFabLabel';
    fabLab.textContent = 'Select Consignee';
    var fabHint = document.createElement('span');
    fabHint.className = 'rixo-fab-trigger-hint';
    fabHint.textContent = 'Tap to choose consignee';
    fabTw.appendChild(fabLab);
    fabTw.appendChild(fabHint);
    var fabChev = document.createElement('span');
    fabChev.className = 'rixo-fab-trigger-chevron';
    fabChev.setAttribute('aria-hidden', 'true');
    fabChev.textContent = '▼';
    fabTrigger.appendChild(fabTw);
    fabTrigger.appendChild(fabChev);
    var fabActions = document.createElement('div');
    fabActions.id = 'bookingConsigneeFabActions';
    fabActions.className = 'rixo-fab-actions';
    fabActions.style.display = 'none';
    fabActions.setAttribute('role', 'listbox');
    fabCol.appendChild(fabTrigger);
    fabCol.appendChild(fabActions);
    fabWrap.appendChild(consigneeSelect);
    fabWrap.appendChild(fabCol);

    consigneeInput.parentNode.insertBefore(consigneeContainer, consigneeInput);
    consigneeContainer.appendChild(fabWrap);
    
    // Remove legacy address preview block if it exists (older sessions)
    const legacyDisplay = document.getElementById('consigneeDisplay');
    if (legacyDisplay) {
      legacyDisplay.remove();
    }
    
    // Hide original input but keep it for form submission (value = consignee name only)
    consigneeInput.style.display = 'none';
    
    // Sync select dropdown to hidden input; POD list follows selected consignee row(s) in booking_mappings
    consigneeSelect.addEventListener('change', function() {
      const sel = this.options[this.selectedIndex];
      if (!sel || sel.value === '') {
        applyConsigneeNameOnly('');
        applyConsigneePodRefresh(false);
        return;
      }
      applyConsigneeNameOnly(sel.textContent || '');
      applyConsigneePodRefresh(false);
    });
    if (typeof window.registerBookingFabSelect === 'function') {
      window.registerBookingFabSelect({
        selectId: 'consigneeSelect',
        wrapId: 'bookingConsigneeFabWrap',
        triggerId: 'bookingConsigneeFabTrigger',
        actionsId: 'bookingConsigneeFabActions',
        labelId: 'bookingConsigneeFabLabel',
        defaultLabel: 'Select Consignee',
        emptyMessage: 'No consignee for this country'
      });
    }
  } else {
    consigneeSelect = document.getElementById('consigneeSelect');
  }
  
  // Populate select dropdown
  consigneeSelect.innerHTML = '<option value="">Select Consignee</option>';
  consignees.forEach((consignee, index) => {
    const option = document.createElement('option');
    option.value = `${index}`;
    option.textContent = consignee.name || '(No Name)';
    consigneeSelect.appendChild(option);
  });
  
  // Auto-fill with first consignee if available
  if (consignees.length > 0) {
    const firstConsignee = consignees[0];
    applyConsigneeNameOnly(firstConsignee.name);
    if (consigneeSelect.options.length > 1) {
      consigneeSelect.selectedIndex = 1;
    }
  } else {
    consigneeInput.value = '';
  }

  if (typeof window.refreshBookingFabSelect === 'function') {
    window.refreshBookingFabSelect('consigneeSelect');
  }
}

/**
 * Store consignee name only (no address block in UI)
 */
function applyConsigneeNameOnly(consigneeName) {
  const consigneeInput = document.getElementById('consigneeName');
  if (consigneeInput) {
    consigneeInput.value = (consigneeName || '').trim();
  }
}

/**
 * @deprecated Use applyConsigneeNameOnly — kept for any inline callers; does not show address
 */
function updateConsigneeDisplay(consigneeName, consigneeAddress) {
  applyConsigneeNameOnly(consigneeName);
  const legacy = document.getElementById('consigneeDisplay');
  if (legacy) {
    legacy.style.display = 'none';
    legacy.innerHTML = '';
  }
}

/**
 * Escape HTML to prevent XSS
 */
function escapeHtml(text) {
  if (!text) return '';
  const div = document.createElement('div');
  div.textContent = text;
  return div.innerHTML;
}

/**
 * Main function to apply booking mappings when country is selected
 */
window.applyBookingMappingsByCountry = async function(country) {
  function refreshBookingMappingFabLabels() {
    if (typeof window.refreshBookingFabSelect !== 'function') return;
    try {
      window.refreshBookingFabSelect('consigneeSelect');
    } catch (e) { /* consignee FAB may not exist yet */ }
    try {
      window.refreshBookingFabSelect('podPort');
    } catch (e) { /* pod FAB may not exist yet */ }
  }
  
  if (!country) {
    console.log('🌍 No country selected, clearing fields');
    window.__carBookingMappingsByCountry = [];
    // Clear fields
    const podSelect = document.getElementById('podPort');
    if (podSelect && podSelect.tagName === 'SELECT') {
      podSelect.innerHTML = '<option value="">Select POD</option>';
      podSelect.value = '';
    } else if (podSelect && podSelect.tagName === 'INPUT') {
      podSelect.value = '';
    }
    const consigneeInput = document.getElementById('consigneeName');
    if (consigneeInput) {
      consigneeInput.value = '';
    }
    const consigneeSelClear = document.getElementById('consigneeSelect');
    if (consigneeSelClear) {
      consigneeSelClear.innerHTML = '<option value="">Select Consignee</option>';
      consigneeSelClear.value = '';
    }
    const legacyDisplay = document.getElementById('consigneeDisplay');
    if (legacyDisplay) {
      legacyDisplay.innerHTML = '';
      legacyDisplay.style.display = 'none';
    }
    refreshBookingMappingFabLabels();
    return;
  }
  
  console.log('🌍 Fetching booking mappings for country:', country);
  
  const mappings = await window.fetchBookingMappingsByCountry(country);
  console.log('📋 Found mappings:', mappings);
  
  if (mappings.length === 0) {
    console.log('⚠️ No mappings found for country:', country);
    window.__carBookingMappingsByCountry = [];
    const legacyDisp = document.getElementById('consigneeDisplay');
    if (legacyDisp) {
      legacyDisp.innerHTML = '';
      legacyDisp.style.display = 'none';
    }
    // When this country has no consignee_map rows: clear consignee + POD completely (no carry-over from previous country).
    if (document.getElementById('consigneeSelect')) {
      populateConsigneeField([]);
    } else {
      applyConsigneeNameOnly('');
      const consigneeInputNm = document.getElementById('consigneeName');
      if (consigneeInputNm) consigneeInputNm.value = '';
    }
    populatePODDropdown([], false);
    refreshBookingMappingFabLabels();
    return;
  }
  
  // Cache full list for consignee → POD filtering (multiple consignees per country, e.g. Kenya)
  window.__carBookingMappingsByCountry = mappings;
  
  // Consignee dropdown first (first consignee auto-selected), then POD from that consignee's row(s) only
  populateConsigneeField(mappings);
  applyConsigneePodRefresh(true);
  
  console.log('✅ Booking mappings applied successfully');
};

/**
 * Initialize booking mapping listeners
 * Note: This is called from Kotlin code to avoid conflicts with existing listeners
 */
window.initBookingMappingAutoFill = function() {
  const countrySelect = document.getElementById('consigneeCountry');
  if (!countrySelect) {
    return;
  }
  
  // Don't clone or replace - Kotlin code handles the listener
  // Just apply mappings if country is already selected
  if (countrySelect.value) {
    setTimeout(() => {
      window.applyBookingMappingsByCountry(countrySelect.value);
    }, 500);
  }
};

// Auto-initialize when the booking form (consignee country) is present
function initializeBookingMapping() {
  if (!document.getElementById('consigneeCountry')) return;
  window.initBookingMappingAutoFill();
}

if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', initializeBookingMapping);
} else {
  initializeBookingMapping();
}

// Also try after delays to catch dynamically loaded content
setTimeout(initializeBookingMapping, 500);
setTimeout(initializeBookingMapping, 1000);
setTimeout(initializeBookingMapping, 2000);
setTimeout(initializeBookingMapping, 3000);

// Also listen for any dynamic content changes
if (window.MutationObserver) {
  const observer = new MutationObserver(function(mutations) {
    const countrySelect = document.getElementById('consigneeCountry');
    if (countrySelect && !countrySelect.hasAttribute('data-booking-mapping-initialized')) {
      countrySelect.setAttribute('data-booking-mapping-initialized', 'true');
      initializeBookingMapping();
    }
  });
  
  observer.observe(document.body, {
    childList: true,
    subtree: true
  });
}

/**
 * Booking page FAB dropdowns — same behavior as Rixo Company picker (avatar + fly-down list).
 */
(function bookingFabModule() {
  var registry = {};

  function cfgFor(selectId) {
    return registry[selectId];
  }

  function closeFab(selectId) {
    var c = cfgFor(selectId);
    if (!c || !c.wrap) return;
    c.wrap.classList.remove('rixo-company-fab--open');
    if (c.actions) c.actions.style.display = 'none';
    if (c.trigger) c.trigger.setAttribute('aria-expanded', 'false');
  }

  /** Close every booking FAB except [exceptSelectId] (omit arg to close all). */
  function closeAllBookingFabsExcept(exceptSelectId) {
    Object.keys(registry).forEach(function(sid) {
      if (exceptSelectId != null && sid === exceptSelectId) return;
      closeFab(sid);
    });
  }

  function rebuildFab(selectId) {
    var c = cfgFor(selectId);
    var select = document.getElementById(selectId);
    if (!select || select.tagName !== 'SELECT' || !c || !c.actions) return;
    c.actions.innerHTML = '';
    var opts = select.querySelectorAll('option');
    var count = 0;
    opts.forEach(function(opt) {
      if (!opt.value) return;
      count++;
      var val = opt.value;
      var labelText = (opt.textContent || opt.value || '').trim();
      var row = document.createElement('button');
      row.type = 'button';
      row.className = 'rixo-fab-action-row rixo-fab-action-row--text-only';
      row.setAttribute('role', 'option');
      row.textContent = labelText;
      row.setAttribute('data-value', val);
      row.setAttribute('title', labelText);
      row.addEventListener('click', function(e) {
        e.stopPropagation();
        select.value = val;
        closeFab(selectId);
        select.dispatchEvent(new Event('change', { bubbles: true }));
        updateLabel(selectId);
      });
      c.actions.appendChild(row);
    });
    if (count === 0 && c.emptyMessage) {
      var empty = document.createElement('div');
      empty.className = 'rixo-fab-action-row';
      empty.style.cssText = 'justify-content:center;font-size:13px;color:#6b7280;padding:8px;';
      empty.textContent = c.emptyMessage;
      c.actions.appendChild(empty);
    }
  }

  function openFab(selectId) {
    var c = cfgFor(selectId);
    if (!c || !c.wrap) return;
    // Opening one FAB must collapse any other (trigger uses stopPropagation so document click never runs).
    closeAllBookingFabsExcept(selectId);
    rebuildFab(selectId);
    c.wrap.classList.add('rixo-company-fab--open');
    if (c.actions) c.actions.style.display = 'flex';
    if (c.trigger) c.trigger.setAttribute('aria-expanded', 'true');
  }

  function toggleFab(selectId) {
    var c = cfgFor(selectId);
    if (!c || !c.wrap) return;
    if (c.wrap.classList.contains('rixo-company-fab--open')) closeFab(selectId);
    else openFab(selectId);
  }

  function updateLabel(selectId) {
    var c = cfgFor(selectId);
    var el = document.getElementById(selectId);
    if (!el || !c) return;
    // During booking restore POD can briefly be an <input>; never throw here.
    if (!el.tagName || el.tagName.toUpperCase() !== 'SELECT') {
      if (c.labelEl) c.labelEl.textContent = c.defaultLabel;
      if (c.letterEl) c.letterEl.textContent = c.defaultLetter || '?';
      return;
    }
    var select = el;
    var v = (select.value || '').trim();
    var display = '';
    if (v) {
      var idx = (typeof select.selectedIndex === 'number') ? select.selectedIndex : -1;
      var opt = (idx >= 0 && select.options && select.options.length > idx) ? select.options[idx] : null;
      display = (opt && (opt.textContent || '').trim()) || v;
    }
    if (c.labelEl) c.labelEl.textContent = display || c.defaultLabel;
    if (c.letterEl) {
      c.letterEl.textContent = display ? display.charAt(0).toUpperCase() : (c.defaultLetter || '?');
    }
  }

  window.registerBookingFabSelect = function(config) {
    var selectId = config.selectId;
    var wrap = document.getElementById(config.wrapId);
    var trigger = document.getElementById(config.triggerId);
    var actions = document.getElementById(config.actionsId);
    if (!wrap || !trigger || !actions || !document.getElementById(selectId)) return;

    registry[selectId] = {
      wrap: wrap,
      trigger: trigger,
      actions: actions,
      labelEl: document.getElementById(config.labelId),
      letterEl: config.letterId ? document.getElementById(config.letterId) : null,
      defaultLabel: config.defaultLabel || 'Select',
      defaultLetter: config.defaultLetter || '?',
      emptyMessage: config.emptyMessage || ''
    };

    if (!trigger.hasAttribute('data-booking-fab-bound')) {
      trigger.setAttribute('data-booking-fab-bound', 'true');
      trigger.addEventListener('click', function(e) {
        e.stopPropagation();
        toggleFab(selectId);
      });
    }

    var sel = document.getElementById(selectId);
    if (sel && !sel.hasAttribute('data-booking-fab-change')) {
      sel.setAttribute('data-booking-fab-change', 'true');
      sel.addEventListener('change', function() {
        updateLabel(selectId);
      });
    }
    updateLabel(selectId);
  };

  window.refreshBookingFabSelect = function(selectId) {
    updateLabel(selectId);
  };

  window.rebuildBookingFabFromSelect = function(selectId) {
    rebuildFab(selectId);
  };

  if (!window.__bookingFabDocClickInstalled) {
    window.__bookingFabDocClickInstalled = true;
    document.addEventListener('click', function(e) {
      Object.keys(registry).forEach(function(sid) {
        var c = registry[sid];
        if (!c || !c.wrap || !c.wrap.classList.contains('rixo-company-fab--open')) return;
        if (c.wrap.contains(e.target)) return;
        closeFab(sid);
      });
    });
    document.addEventListener('keydown', function(e) {
      if (e.key !== 'Escape') return;
      Object.keys(registry).forEach(function(sid) {
        closeFab(sid);
      });
    });
  }

  window.ensureBookingFabPod = function() {
    var sel = document.getElementById('podPort');
    if (!sel || sel.tagName !== 'SELECT') return;

    if (document.getElementById('bookingPodFabWrap')) {
      window.registerBookingFabSelect({
        selectId: 'podPort',
        wrapId: 'bookingPodFabWrap',
        triggerId: 'bookingPodFabTrigger',
        actionsId: 'bookingPodFabActions',
        labelId: 'bookingPodFabLabel',
        defaultLabel: 'Select POD',
        emptyMessage: 'No POD for this consignee'
      });
      return;
    }

    var parent = sel.parentNode;
    if (!parent) return;
    var wrap = document.createElement('div');
    wrap.className = 'rixo-company-fab-wrap booking-fab-field';
    wrap.id = 'bookingPodFabWrap';
    var col = document.createElement('div');
    col.className = 'rixo-company-fab';
    var trigger = document.createElement('button');
    trigger.type = 'button';
    trigger.className = 'rixo-fab-trigger';
    trigger.id = 'bookingPodFabTrigger';
    trigger.setAttribute('aria-expanded', 'false');
    trigger.setAttribute('aria-haspopup', 'listbox');
    trigger.setAttribute('aria-controls', 'bookingPodFabActions');
    var tw = document.createElement('span');
    tw.className = 'rixo-fab-trigger-text-wrap';
    var lab = document.createElement('span');
    lab.className = 'rixo-fab-trigger-label';
    lab.id = 'bookingPodFabLabel';
    lab.textContent = 'Select POD';
    var hint = document.createElement('span');
    hint.className = 'rixo-fab-trigger-hint';
    hint.textContent = 'Tap to choose POD';
    tw.appendChild(lab);
    tw.appendChild(hint);
    var chev = document.createElement('span');
    chev.className = 'rixo-fab-trigger-chevron';
    chev.setAttribute('aria-hidden', 'true');
    chev.textContent = '▼';
    trigger.appendChild(tw);
    trigger.appendChild(chev);
    var actions = document.createElement('div');
    actions.id = 'bookingPodFabActions';
    actions.className = 'rixo-fab-actions';
    actions.style.display = 'none';
    actions.setAttribute('role', 'listbox');
    col.appendChild(trigger);
    col.appendChild(actions);
    parent.insertBefore(wrap, sel);
    sel.className = 'rixo-company-fab-native-select';
    sel.setAttribute('tabindex', '-1');
    sel.setAttribute('aria-hidden', 'true');
    wrap.appendChild(sel);
    wrap.appendChild(col);

    window.registerBookingFabSelect({
      selectId: 'podPort',
      wrapId: 'bookingPodFabWrap',
      triggerId: 'bookingPodFabTrigger',
      actionsId: 'bookingPodFabActions',
      labelId: 'bookingPodFabLabel',
      defaultLabel: 'Select POD',
      emptyMessage: 'No POD for this consignee'
    });
  };
})();
