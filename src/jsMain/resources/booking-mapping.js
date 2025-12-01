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
    
    // Cache the results
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
    .map(m => m.pod)
    .filter(pod => pod && pod.trim().length > 0)
    .map(pod => pod.trim());
  
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
    podInput.parentNode.replaceChild(select, podInput);
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
  
  // Auto-select first POD if available
  if (pods.length > 0 && !podSelect.value) {
    podSelect.value = pods[0];
    // Trigger change event for any listeners
    podSelect.dispatchEvent(new Event('change', { bubbles: true }));
  }
}

/**
 * Populate CONSIGNEE field and dropdown
 */
function populateConsigneeField(mappings) {
  const consigneeInput = document.getElementById('consigneeName');
  if (!consigneeInput) return;
  
  const consignees = getUniqueConsignees(mappings);
  
  // Create dropdown container if it doesn't exist
  let consigneeContainer = document.getElementById('consigneeContainer');
  let consigneeSelect = document.getElementById('consigneeSelect');
  let consigneeDisplay = document.getElementById('consigneeDisplay');
  
  if (!consigneeContainer) {
    // Create container div
    consigneeContainer = document.createElement('div');
    consigneeContainer.id = 'consigneeContainer';
    consigneeContainer.style.cssText = 'position: relative; width: 100%;';
    
    // Create select dropdown for consignee selection
    consigneeSelect = document.createElement('select');
    consigneeSelect.id = 'consigneeSelect';
    consigneeSelect.style.cssText = 'width: 100%; padding: 10px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; margin-bottom: 8px; background-color: white;';
    
    // Insert dropdown before the input
    consigneeInput.parentNode.insertBefore(consigneeContainer, consigneeInput);
    consigneeContainer.appendChild(consigneeSelect);
    
    // Create display div for formatted consignee info (replaces the input visually)
    consigneeDisplay = document.createElement('div');
    consigneeDisplay.id = 'consigneeDisplay';
    consigneeDisplay.contentEditable = 'true';
    consigneeDisplay.style.cssText = consigneeInput.style.cssText + '; min-height: 60px; line-height: 1.6; padding: 10px;';
    consigneeDisplay.setAttribute('placeholder', '(CONSIGNEE NAME)');
    
    // Insert display div after the select
    consigneeContainer.appendChild(consigneeDisplay);
    
    // Hide original input but keep it for form submission
    consigneeInput.style.display = 'none';
    
    // Sync display to hidden input when content changes
    // Extract only text content, removing HTML tags and extra whitespace
    consigneeDisplay.addEventListener('blur', function() {
      // Get text content and clean it up
      const textContent = (this.innerText || this.textContent || '').trim();
      // Split by newline to separate name and address
      const lines = textContent.split('\n').map(line => line.trim()).filter(line => line.length > 0);
      
      if (lines.length >= 2) {
        // Name is first line, address is rest
        consigneeInput.value = lines[0] + '\n' + lines.slice(1).join(' ');
      } else if (lines.length === 1) {
        // Only name, no address
        consigneeInput.value = lines[0];
      } else {
        consigneeInput.value = '';
      }
    });
    
    // Sync select dropdown to display
    consigneeSelect.addEventListener('change', function() {
      const selectedIndex = this.selectedIndex - 1; // -1 for "Select Consignee" option
      if (selectedIndex >= 0 && selectedIndex < consignees.length) {
        const selected = consignees[selectedIndex];
        updateConsigneeDisplay(selected.name, selected.address);
      }
    });
  } else {
    consigneeSelect = document.getElementById('consigneeSelect');
    consigneeDisplay = document.getElementById('consigneeDisplay');
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
    updateConsigneeDisplay(firstConsignee.name, firstConsignee.address);
    if (consigneeSelect.options.length > 1) {
      consigneeSelect.selectedIndex = 1; // Select first actual option
    }
  } else {
    if (consigneeDisplay) {
      consigneeDisplay.innerHTML = '';
    }
    consigneeInput.value = '';
  }
}

/**
 * Update consignee display with formatted text (name in bold, address below)
 */
function updateConsigneeDisplay(consigneeName, consigneeAddress) {
  const consigneeDisplay = document.getElementById('consigneeDisplay');
  const consigneeInput = document.getElementById('consigneeName');
  
  if (!consigneeDisplay) return;
  
  // Build HTML with formatted text
  let html = '';
  if (consigneeName) {
    html += `<strong>${escapeHtml(consigneeName)}</strong>`;
  }
  if (consigneeAddress) {
    if (html) html += '<br>';
    html += escapeHtml(consigneeAddress);
  }
  
  consigneeDisplay.innerHTML = html;
  
  // Also update hidden input for form submission
  // Only store the exact name and address passed to this function, nothing else
  if (consigneeInput) {
    // Clean and join name and address with newline separator
    const cleanName = (consigneeName || '').trim();
    const cleanAddress = (consigneeAddress || '').trim();
    
    // Only join if both exist, otherwise use just the name
    if (cleanName && cleanAddress) {
      consigneeInput.value = cleanName + '\n' + cleanAddress;
    } else if (cleanName) {
      consigneeInput.value = cleanName;
    } else {
      consigneeInput.value = '';
    }
    
    console.log('📋 updateConsigneeDisplay - Updated consigneeInput.value:', consigneeInput.value);
    console.log('📋 updateConsigneeDisplay - Name:', cleanName);
    console.log('📋 updateConsigneeDisplay - Address:', cleanAddress);
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
  if (!country) {
    console.log('🌍 No country selected, clearing fields');
    // Clear fields
    const podSelect = document.getElementById('podPort');
    if (podSelect && podSelect.tagName === 'SELECT') {
      podSelect.innerHTML = '<option value="">Select POD</option>';
    }
    const consigneeDisplay = document.getElementById('consigneeDisplay');
    if (consigneeDisplay) {
      consigneeDisplay.innerHTML = '';
    }
    const consigneeInput = document.getElementById('consigneeName');
    if (consigneeInput) {
      consigneeInput.value = '';
    }
    return;
  }
  
  console.log('🌍 Fetching booking mappings for country:', country);
  
  const mappings = await window.fetchBookingMappingsByCountry(country);
  console.log('📋 Found mappings:', mappings);
  
  if (mappings.length === 0) {
    console.log('⚠️ No mappings found for country:', country);
    // Clear fields if no mappings
    const podSelect = document.getElementById('podPort');
    if (podSelect && podSelect.tagName === 'SELECT') {
      podSelect.innerHTML = '<option value="">Select POD</option>';
    }
    const consigneeDisplay = document.getElementById('consigneeDisplay');
    if (consigneeDisplay) {
      consigneeDisplay.innerHTML = '';
    }
    return;
  }
  
  // Populate POD dropdown
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
  console.log('🔧 Initializing booking mapping auto-fill...');
  const countrySelect = document.getElementById('consigneeCountry');
  
  if (!countrySelect) {
    console.log('⚠️ consigneeCountry element not found');
    return;
  }
  
  console.log('✅ Found consigneeCountry element:', countrySelect);
  
  // Don't clone or replace - Kotlin code handles the listener
  // Just apply mappings if country is already selected
  if (countrySelect.value) {
    console.log('🌍 Initial country value found:', countrySelect.value);
    setTimeout(() => {
      window.applyBookingMappingsByCountry(countrySelect.value);
    }, 500);
  }
  
  console.log('✅ Booking mapping auto-fill initialized');
};

// Auto-initialize multiple times to catch different load states
function initializeBookingMapping() {
  console.log('🚀 Attempting to initialize booking mapping...');
  window.initBookingMappingAutoFill();
}

// Try immediately
if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', function() {
    console.log('📄 DOM loaded, initializing booking mapping...');
    initializeBookingMapping();
  });
} else {
  console.log('📄 DOM already loaded, initializing booking mapping...');
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
      console.log('🔄 Detected consigneeCountry element, initializing...');
      countrySelect.setAttribute('data-booking-mapping-initialized', 'true');
      initializeBookingMapping();
    }
  });
  
  observer.observe(document.body, {
    childList: true,
    subtree: true
  });
  
  console.log('👀 MutationObserver set up to watch for consigneeCountry element');
}
