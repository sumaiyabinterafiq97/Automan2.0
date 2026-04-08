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
 * Populate POD dropdown with unique POD values
 */
function populatePODDropdown(mappings) {
  const podInput = document.getElementById('podPort');
  if (!podInput) return;
  
  const pods = getUniquePODs(mappings);
  
  // Preserve current value before converting/updating
  const currentValue = podInput.value ? podInput.value.trim() : '';
  
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
    consigneeContainer.style.cssText = 'position: relative; width: 100%;';
    
    // Create select dropdown for consignee selection
    consigneeSelect = document.createElement('select');
    consigneeSelect.id = 'consigneeSelect';
    consigneeSelect.style.cssText = 'width: 100%; padding: 10px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; background-color: white;';
    
    // Insert dropdown before the input
    consigneeInput.parentNode.insertBefore(consigneeContainer, consigneeInput);
    consigneeContainer.appendChild(consigneeSelect);
    
    // Remove legacy address preview block if it exists (older sessions)
    const legacyDisplay = document.getElementById('consigneeDisplay');
    if (legacyDisplay) {
      legacyDisplay.remove();
    }
    
    // Hide original input but keep it for form submission (value = consignee name only)
    consigneeInput.style.display = 'none';
    
    // Sync select dropdown to hidden input (name only; use option text so list refreshes stay correct)
    consigneeSelect.addEventListener('change', function() {
      const sel = this.options[this.selectedIndex];
      if (!sel || sel.value === '') {
        applyConsigneeNameOnly('');
        return;
      }
      applyConsigneeNameOnly(sel.textContent || '');
    });
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
  // Preserve POD value before any operations
  const podElement = document.getElementById('podPort');
  const preservedPodValue = podElement ? (podElement.value ? podElement.value.trim() : '') : '';
  
  if (!country) {
    console.log('🌍 No country selected, clearing fields');
    // Clear fields
    const podSelect = document.getElementById('podPort');
    if (podSelect && podSelect.tagName === 'SELECT') {
      podSelect.innerHTML = '<option value="">Select POD</option>';
    }
    const consigneeInput = document.getElementById('consigneeName');
    if (consigneeInput) {
      consigneeInput.value = '';
    }
    const legacyDisplay = document.getElementById('consigneeDisplay');
    if (legacyDisplay) {
      legacyDisplay.innerHTML = '';
      legacyDisplay.style.display = 'none';
    }
    return;
  }
  
  console.log('🌍 Fetching booking mappings for country:', country);
  
  const mappings = await window.fetchBookingMappingsByCountry(country);
  console.log('📋 Found mappings:', mappings);
  
  if (mappings.length === 0) {
    console.log('⚠️ No mappings found for country:', country);
    // Clear fields if no mappings, but preserve POD if it was manually entered
    const podSelect = document.getElementById('podPort');
    if (podSelect) {
      if (podSelect.tagName === 'SELECT') {
        podSelect.innerHTML = '<option value="">Select POD</option>';
        // Restore preserved value if it exists
        if (preservedPodValue && preservedPodValue !== '') {
          const option = document.createElement('option');
          option.value = preservedPodValue;
          option.textContent = preservedPodValue;
          podSelect.appendChild(option);
          podSelect.value = preservedPodValue;
          console.log('✅ Preserved POD value (no mappings):', preservedPodValue);
        }
      } else {
        // For input element, just restore the value
        podSelect.value = preservedPodValue;
      }
    }
    const consigneeInputNm = document.getElementById('consigneeName');
    if (consigneeInputNm) consigneeInputNm.value = '';
    const legacyDisp = document.getElementById('consigneeDisplay');
    if (legacyDisp) {
      legacyDisp.innerHTML = '';
      legacyDisp.style.display = 'none';
    }
    return;
  }
  
  // Populate POD dropdown (will preserve value if it exists)
  populatePODDropdown(mappings);
  
  // Populate CONSIGNEE field and dropdown
  populateConsigneeField(mappings);
  
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
