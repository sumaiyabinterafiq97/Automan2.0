// Booking Mapping Modal - Manage POD, Consignee Name, and Consignee Address for countries

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

// Global variable to track current country being managed
window.currentBookingCountry = null;
window.currentEditingMappingId = null;

// Show booking mappings modal for a specific country
window.showBookingMappingsModal = function(country) {
    if (!country) {
        console.error('No country provided to showBookingMappingsModal');
        return;
    }
    
    window.currentBookingCountry = country;
    window.currentEditingMappingId = null;
    
    // Create modal if it doesn't exist
    let modal = document.getElementById('bookingMappingModal');
    if (!modal) {
        createBookingMappingModal();
        modal = document.getElementById('bookingMappingModal');
    }
    
    const title = document.getElementById('bookingModalTitle');
    const body = document.querySelector('#bookingMappingModal .modal-body');
    
    if (title) {
        title.textContent = `Manage Mappings for: ${country}`;
    }
    
    if (body) {
        body.innerHTML = `
            <div class="mapping-section">
                <div style="margin-bottom: 16px;">
                    <h4 style="margin:0 0 12px 0;">Current Mappings</h4>
                    <div id="currentBookingMappings" class="current-mappings">
                        <p style="color: #666; text-align: center; padding: 20px;">Loading mappings...</p>
                    </div>
                </div>
                
                <div class="add-mapping-section">
                    <h4>Add New Mapping</h4>
                    <div class="mapping-form" style="display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin-top: 12px;">
                        <input type="text" id="newPod" placeholder="POD (Port of Discharge)" required style="padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                        <input type="text" id="newConsigneeName" placeholder="Consignee Name" required style="padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                        <textarea id="newConsigneeAddress" placeholder="Consignee Address" rows="3" style="grid-column: 1 / -1; padding: 8px; border: 1px solid #ddd; border-radius: 4px; resize: vertical;"></textarea>
                        <button type="button" id="addBookingMapping" style="grid-column: 1 / -1; padding: 10px; background-color: #3b82f6; color: white; border: none; border-radius: 4px; cursor: pointer; font-weight: 500;">Add Mapping</button>
                    </div>
                </div>
            </div>
        `;
    }
    
    // Load current mappings
    loadCurrentBookingMappings(country);
    
    // Show modal
    if (modal) {
        modal.style.display = 'block';
    }
    
    // Setup event listeners
    setupBookingModalEventListeners();
};

// Create booking mapping modal structure
function createBookingMappingModal() {
    const modal = document.createElement('div');
    modal.id = 'bookingMappingModal';
    modal.className = 'modal';
    modal.innerHTML = `
        <div class="modal-content" style="max-width: 900px;">
            <div class="modal-header">
                <h3 id="bookingModalTitle">Manage Mappings</h3>
                <span class="close" id="closeBookingModal">&times;</span>
            </div>
            <div class="modal-body">
                <!-- Content will be populated dynamically -->
            </div>
            <div class="modal-footer">
                <button type="button" id="saveBookingChanges" class="btn btn-primary">Save Changes</button>
                <button type="button" id="cancelBookingModal" class="btn btn-secondary">Cancel</button>
            </div>
        </div>
    `;
    document.body.appendChild(modal);
    
    // Close modal when clicking outside
    modal.addEventListener('click', function(e) {
        if (e.target === modal) {
            closeBookingMappingModal();
        }
    });
    
    // Close modal when clicking X button
    const closeBtn = document.getElementById('closeBookingModal');
    if (closeBtn) {
        closeBtn.addEventListener('click', closeBookingMappingModal);
    }
}

// Load current booking mappings for a country
async function loadCurrentBookingMappings(country) {
    const container = document.getElementById('currentBookingMappings');
    if (!container) return;
    
    try {
        const response = await fetch(window.apiUrl(`booking/mappings/by-country/${encodeURIComponent(country)}`));
        const result = await response.json();
        
        if (result.success && result.data && result.data.length > 0) {
            const mappings = result.data;
            container.innerHTML = `
                <table style="width: 100%; border-collapse: collapse; margin-bottom: 16px;">
                    <thead>
                        <tr style="background-color: #f3f4f6; border-bottom: 2px solid #e5e7eb;">
                            <th style="padding: 10px; text-align: left; font-weight: 600; color: #374151;">POD</th>
                            <th style="padding: 10px; text-align: left; font-weight: 600; color: #374151;">Consignee Name</th>
                            <th style="padding: 10px; text-align: left; font-weight: 600; color: #374151;">Consignee Address</th>
                            <th style="padding: 10px; text-align: center; font-weight: 600; color: #374151;">Actions</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${mappings.map(mapping => `
                            <tr style="border-bottom: 1px solid #e5e7eb;">
                                <td style="padding: 10px; color: #111827;">${escapeHtml(mapping.pod || '-')}</td>
                                <td style="padding: 10px; color: #111827;">${escapeHtml(mapping.consigneeName || '-')}</td>
                                <td style="padding: 10px; color: #111827; max-width: 300px; word-wrap: break-word;">${escapeHtml(mapping.consigneeAddress || '-')}</td>
                                <td style="padding: 10px; text-align: center;">
                                    <button class="btn-edit" data-id="${mapping.id}" style="margin-right: 8px; padding: 6px 12px; background-color: #fbbf24; color: white; border: none; border-radius: 4px; cursor: pointer;">Edit</button>
                                    <button class="btn-delete" data-id="${mapping.id}" style="padding: 6px 12px; background-color: #ef4444; color: white; border: none; border-radius: 4px; cursor: pointer;">Delete</button>
                                </td>
                            </tr>
                        `).join('')}
                    </tbody>
                </table>
            `;
            
            // Attach event listeners to Edit and Delete buttons
            attachBookingMappingActions();
        } else {
            container.innerHTML = '<p style="color: #666; text-align: center; padding: 20px;">No mappings found for this country.</p>';
        }
    } catch (error) {
        console.error('Error loading booking mappings:', error);
        container.innerHTML = '<p style="color: #ef4444; text-align: center; padding: 20px;">Error loading mappings. Please try again.</p>';
    }
}

// Attach event listeners to Edit and Delete buttons
function attachBookingMappingActions() {
    // Edit buttons
    document.querySelectorAll('.btn-edit').forEach(btn => {
        btn.addEventListener('click', function() {
            const mappingId = this.getAttribute('data-id');
            editBookingMapping(mappingId);
        });
    });
    
    // Delete buttons
    document.querySelectorAll('.btn-delete').forEach(btn => {
        btn.addEventListener('click', function() {
            const mappingId = this.getAttribute('data-id');
            deleteBookingMapping(mappingId);
        });
    });
}

// Edit booking mapping
async function editBookingMapping(mappingId) {
    if (!mappingId || !window.currentBookingCountry) return;
    
    try {
        // Fetch the mapping data
        const response = await fetch(window.apiUrl(`booking/mappings/by-country/${encodeURIComponent(window.currentBookingCountry)}`));
        const result = await response.json();
        
        if (result.success && result.data) {
            const mapping = result.data.find(m => m.id == mappingId);
            if (mapping) {
                // Populate form fields
                document.getElementById('newPod').value = mapping.pod || '';
                document.getElementById('newConsigneeName').value = mapping.consigneeName || '';
                document.getElementById('newConsigneeAddress').value = mapping.consigneeAddress || '';
                
                // Set editing mode
                window.currentEditingMappingId = mappingId;
                
                // Change button text
                const addBtn = document.getElementById('addBookingMapping');
                if (addBtn) {
                    addBtn.textContent = 'Update Mapping';
                }
                
                // Scroll to form
                document.querySelector('.add-mapping-section').scrollIntoView({ behavior: 'smooth', block: 'start' });
            }
        }
    } catch (error) {
        console.error('Error loading mapping for edit:', error);
        alert('Error loading mapping details. Please try again.');
    }
}

// Delete booking mapping
async function deleteBookingMapping(mappingId) {
    const country = window.currentBookingCountry;
    if (!mappingId) return;
    
    if (!confirm('Are you sure you want to delete this mapping?')) {
        return;
    }
    
    try {
        const response = await fetch(window.apiUrl(`booking/mappings/${mappingId}`), {
            method: 'DELETE'
        });
        
        const result = await response.json();
        
        if (result.success) {
            // Clear the cache for this country to force refresh
            if (country && window.bookingMappingsCache && window.bookingMappingsCache[country]) {
                delete window.bookingMappingsCache[country];
                console.log('🗑️ Cleared cache for country:', country);
            }
            
            // Reload mappings in modal
            if (window.currentBookingCountry) {
                loadCurrentBookingMappings(window.currentBookingCountry);
            }
            
            // Re-apply mappings to refresh the main form dropdown
            if (country && window.applyBookingMappingsByCountry) {
                console.log('🔄 Refreshing consignee dropdown after deleting mapping...');
                setTimeout(() => {
                    window.applyBookingMappingsByCountry(country);
                }, 300); // Small delay to ensure backend has processed the change
            }
            
            alert('Mapping deleted successfully.');
        } else {
            alert('Error deleting mapping: ' + (result.message || 'Unknown error'));
        }
    } catch (error) {
        console.error('Error deleting mapping:', error);
        alert('Error deleting mapping. Please try again.');
    }
}

// Add new booking mapping
async function addBookingMapping() {
    const country = window.currentBookingCountry;
    if (!country) {
        alert('No country selected.');
        return;
    }
    
    const pod = document.getElementById('newPod').value.trim();
    const consigneeName = document.getElementById('newConsigneeName').value.trim();
    const consigneeAddress = document.getElementById('newConsigneeAddress').value.trim();
    
    if (!pod || !consigneeName) {
        alert('POD and Consignee Name are required.');
        return;
    }
    
    const mappingData = {
        country: country,
        pod: pod,
        consigneeName: consigneeName,
        consigneeAddress: consigneeAddress,
        clientName: null,  // Ignored for now
        pols: null,        // Ignored for now
        stockLocation: null // Ignored for now
    };
    
    try {
        let url = window.apiUrl('booking/mappings/add');
        let method = 'POST';
        
        // If editing, update instead of create
        if (window.currentEditingMappingId) {
            url = window.apiUrl(`booking/mappings/${window.currentEditingMappingId}`);
            method = 'PUT';
            mappingData.id = window.currentEditingMappingId;
        }
        
        const response = await fetch(url, {
            method: method,
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(mappingData)
        });
        
        const result = await response.json();
        
        if (result.success) {
            // Clear the cache for this country to force refresh
            if (window.bookingMappingsCache && window.bookingMappingsCache[country]) {
                delete window.bookingMappingsCache[country];
                console.log('🗑️ Cleared cache for country:', country);
            }
            
            // Clear form
            document.getElementById('newPod').value = '';
            document.getElementById('newConsigneeName').value = '';
            document.getElementById('newConsigneeAddress').value = '';
            window.currentEditingMappingId = null;
            
            // Reset button text
            const addBtn = document.getElementById('addBookingMapping');
            if (addBtn) {
                addBtn.textContent = 'Add Mapping';
            }
            
            // Reload mappings in modal
            loadCurrentBookingMappings(country);
            
            // Re-apply mappings to refresh the main form dropdown
            if (window.applyBookingMappingsByCountry) {
                console.log('🔄 Refreshing consignee dropdown after adding mapping...');
                setTimeout(() => {
                    window.applyBookingMappingsByCountry(country);
                }, 300); // Small delay to ensure backend has processed the change
            }
            
            alert(window.currentEditingMappingId ? 'Mapping updated successfully.' : 'Mapping added successfully.');
        } else {
            alert('Error saving mapping: ' + (result.message || 'Unknown error'));
        }
    } catch (error) {
        console.error('Error saving mapping:', error);
        alert('Error saving mapping. Please try again.');
    }
}

// Setup event listeners for booking modal
function setupBookingModalEventListeners() {
    // Add/Update Mapping button
    const addBtn = document.getElementById('addBookingMapping');
    if (addBtn) {
        // Remove existing listeners
        const newAddBtn = addBtn.cloneNode(true);
        addBtn.parentNode.replaceChild(newAddBtn, addBtn);
        newAddBtn.addEventListener('click', addBookingMapping);
    }
    
    // Close button
    const closeBtn = document.getElementById('closeBookingModal');
    if (closeBtn) {
        closeBtn.onclick = closeBookingMappingModal;
    }
    
    // Cancel button
    const cancelBtn = document.getElementById('cancelBookingModal');
    if (cancelBtn) {
        cancelBtn.onclick = closeBookingMappingModal;
    }
    
    // Save Changes button (currently just closes modal, as changes are saved immediately)
    const saveBtn = document.getElementById('saveBookingChanges');
    if (saveBtn) {
        saveBtn.onclick = closeBookingMappingModal;
    }
}

// Close booking mapping modal
function closeBookingMappingModal() {
    const modal = document.getElementById('bookingMappingModal');
    if (modal) {
        modal.style.display = 'none';
    }
    
    // Reset state
    window.currentBookingCountry = null;
    window.currentEditingMappingId = null;
}

// Escape HTML to prevent XSS
function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

