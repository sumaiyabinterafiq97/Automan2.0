// Dynamic Rixo Price Mapping Data
// Generated from import_rixo_data.sql with hierarchical filtering

// API Base URL - use relative path so nginx can proxy to backend
const API_BASE_URL = '/api';

// Helper function to get API URL
function apiUrl(path) {
    const cleanPath = path.startsWith('/') ? path.substring(1) : path;
    return `${API_BASE_URL}/${cleanPath}`;
}

/**
 * Edit-form guard: Rixo auto-mapping must not stomp user edits or the confirm-save window.
 * - window.__suppressRixoAutoSelect: set true while confirm modal is open and until PUT completes.
 * - window.__editRixoFieldOverrides: per-field user overrides (edit* element ids).
 */
window.__editRixoFieldOverrides = window.__editRixoFieldOverrides || {};

window.markEditRixoFieldUserOverride = function(editFieldId) {
    if (!editFieldId) return;
    window.__editRixoFieldOverrides = window.__editRixoFieldOverrides || {};
    window.__editRixoFieldOverrides[String(editFieldId)] = true;
};

window.resetEditRixoOverridesAfterSupplierChange = function() {
    var o = window.__editRixoFieldOverrides;
    if (!o || typeof o !== 'object') return;
    delete o.editStockLocation;
    delete o.editPol;
    delete o.editVenueId;
    delete o.editRixoCompany;
    delete o.editShipmentSize;
    window.__rixoPriceUserOverride = false;
};

/** Nested counter: blocks cascade listeners during programmatic rebuild/restore/apply. */
window.__supplierMapProgrammaticDepth = 0;

window.beginSupplierMapProgrammatic = function() {
    window.__supplierMapProgrammaticDepth = (window.__supplierMapProgrammaticDepth || 0) + 1;
    window.__suppressRixoAutoSelect = true;
};

window.endSupplierMapProgrammatic = function() {
    window.__supplierMapProgrammaticDepth = Math.max(0, (window.__supplierMapProgrammaticDepth || 1) - 1);
    if (window.__supplierMapProgrammaticDepth <= 0) {
        window.__supplierMapProgrammaticDepth = 0;
        window.__suppressRixoAutoSelect = false;
    }
};

window.forceEndSupplierMapProgrammatic = function() {
    window.__supplierMapProgrammaticDepth = 0;
    window.__suppressRixoAutoSelect = false;
};

window.isSupplierMapProgrammaticUpdate = function() {
    return (window.__supplierMapProgrammaticDepth || 0) > 0;
};

function __rixoSkipAutoEditField(editFieldId) {
    if (!editFieldId) return false;
    var id = String(editFieldId);
    if (id.indexOf('edit') !== 0) return false;
    // Open-dropdown rebuild must refresh option lists even when suppress/override are set.
    if (typeof isSupplierMasterForceBuild === 'function' && isSupplierMasterForceBuild(id)) return false;
    // User-initiated supplier apply must write edit* fields even while programmatic guard is active.
    if (window.__supplierApplyInFlight === true) return false;
    if (window.__suppressRixoAutoSelect === true) return true;
    var o = window.__editRixoFieldOverrides;
    return !!(o && typeof o === 'object' && o[id] === true);
}

window.rixoPriceMapping = {
    "ARAI BAYSIDE": {
        typeOfVehicle: ['CAR'],
        stockLocation: ['GLOBAL KAWASAKI'],
        rixoCompany: ['YAMAZAKI', 'SHAHBAZ'],
        rixoPrice: ['¥3,000'],
        venueId: ['22431'],
        mappings: [
            {
                typeOfVehicle: 'CAR',
                rixoCompany: 'YAMAZAKI',
                rixoPrice: '¥3,000',
                venueId: '22431',
                stockLocation: 'GLOBAL KAWASAKI'
            },
            {
                typeOfVehicle: 'CAR',
                rixoCompany: 'SHAHBAZ',
                rixoPrice: '¥3,000',
                venueId: '22431',
                stockLocation: 'GLOBAL KAWASAKI'
            },
        ]
    },
    "ARAI BAYSIDE (DAI 2 YARD)": {
        typeOfVehicle: ['CAR'],
        stockLocation: ['GLOBAL KAWASAKI'],
        rixoCompany: ['SHAHBAZ'],
        rixoPrice: ['¥3,500'],
        venueId: ['22431'],
        mappings: [
            {
                typeOfVehicle: 'CAR',
                rixoCompany: 'SHAHBAZ',
                rixoPrice: '¥3,500',
                venueId: '22431',
                stockLocation: 'GLOBAL KAWASAKI'
            },
        ]
    },
    "ARAI OYAMA": {
        typeOfVehicle: ['CAR', 'HIACE', 'TRUCK'],
        stockLocation: ['GLOBAL KAWASAKI'],
        rixoCompany: ['YAMAZAKI', 'SHAHBAZ'],
        rixoPrice: ['¥10,000', '¥12,000', '¥18,000'],
        venueId: ['22431'],
        mappings: [
            {
                typeOfVehicle: 'CAR',
                rixoCompany: 'YAMAZAKI',
                rixoPrice: '¥10,000',
                venueId: '22431',
                stockLocation: 'GLOBAL KAWASAKI'
            },
            {
                typeOfVehicle: 'CAR',
                rixoCompany: 'SHAHBAZ',
                rixoPrice: '¥10,000',
                venueId: '22431',
                stockLocation: 'GLOBAL KAWASAKI'
            },
            {
                typeOfVehicle: 'HIACE',
                rixoCompany: 'SHAHBAZ',
                rixoPrice: '¥12,000',
                venueId: '22431',
                stockLocation: 'GLOBAL KAWASAKI'
            },
            {
                typeOfVehicle: 'TRUCK',
                rixoCompany: 'SHAHBAZ',
                rixoPrice: '¥18,000',
                venueId: '22431',
                stockLocation: 'GLOBAL KAWASAKI'
            },
        ]
    },
    "ARAI SENDAI": {
        typeOfVehicle: ['-'],
        stockLocation: ['GLOBAL KAWASAKI'],
        rixoCompany: ['LOGICO'],
        rixoPrice: ['¥19,800'],
        venueId: ['22431'],
        mappings: [
            {
                typeOfVehicle: '-',
                rixoCompany: 'LOGICO',
                rixoPrice: '¥19,800',
                venueId: '22431',
                stockLocation: 'GLOBAL KAWASAKI'
            },
        ]
    },
    "AUCNETVAA (KISARAZU)": {
        typeOfVehicle: ['CAR'],
        stockLocation: ['GLOBAL KAWASAKI'],
        rixoCompany: ['YAMAZAKI'],
        rixoPrice: ['¥8,000'],
        venueId: ['A052166'],
        mappings: [
            {
                typeOfVehicle: 'CAR',
                rixoCompany: 'YAMAZAKI',
                rixoPrice: '¥8,000',
                venueId: 'A052166',
                stockLocation: 'GLOBAL KAWASAKI'
            },
        ]
    },
    "AUCNETVAA (SAKURA)": {
        typeOfVehicle: ['CAR'],
        stockLocation: ['GLOBAL KAWASAKI'],
        rixoCompany: ['YAMAZAKI'],
        rixoPrice: ['¥8,000'],
        venueId: ['A052166'],
        mappings: [
            {
                typeOfVehicle: 'CAR',
                rixoCompany: 'YAMAZAKI',
                rixoPrice: '¥8,000',
                venueId: 'A052166',
                stockLocation: 'GLOBAL KAWASAKI'
            },
        ]
    },
    "BAYAUC": {
        typeOfVehicle: ['CAR / BIG CAR'],
        stockLocation: ['KLC'],
        rixoCompany: ['KLC'],
        rixoPrice: ['¥5,000'],
        venueId: ['24016'],
        mappings: [
            {
                typeOfVehicle: 'CAR / BIG CAR',
                rixoCompany: 'KLC',
                rixoPrice: '¥5,000',
                venueId: '24016',
                stockLocation: 'KLC'
            },
        ]
    },
    "CAA CHUBU": {
        typeOfVehicle: ['CAR / BIG CAR'],
        stockLocation: ['GLOBAL NAGOYA'],
        rixoCompany: ['STYLISH AUTO'],
        rixoPrice: ['¥6,500'],
        venueId: ['T008288'],
        mappings: [
            {
                typeOfVehicle: 'CAR / BIG CAR',
                rixoCompany: 'STYLISH AUTO',
                rixoPrice: '¥6,500',
                venueId: 'T008288',
                stockLocation: 'GLOBAL NAGOYA'
            },
        ]
    },
    "CAA GIFU": {
        typeOfVehicle: ['CAR / BIG CAR'],
        stockLocation: ['GLOBAL NAGOYA'],
        rixoCompany: ['STYLISH AUTO'],
        rixoPrice: ['¥7,500'],
        venueId: ['T008288'],
        mappings: [
            {
                typeOfVehicle: 'CAR / BIG CAR',
                rixoCompany: 'STYLISH AUTO',
                rixoPrice: '¥7,500',
                venueId: 'T008288',
                stockLocation: 'GLOBAL NAGOYA'
            },
        ]
    },
    "CAA TOHOKU": {
        typeOfVehicle: ['CAR / BIG CAR'],
        stockLocation: ['GLOBAL KAWASAKI'],
        rixoCompany: ['LOGICO'],
        rixoPrice: ['¥36,000'],
        venueId: ['T008288'],
        mappings: [
            {
                typeOfVehicle: 'CAR / BIG CAR',
                rixoCompany: 'LOGICO',
                rixoPrice: '¥36,000',
                venueId: 'T008288',
                stockLocation: 'GLOBAL KAWASAKI'
            },
        ]
    },
    "CAA TOKYO": {
        typeOfVehicle: ['CAR / BIG CAR', 'TRUCK'],
        stockLocation: ['GLOBAL KAWASAKI'],
        rixoCompany: ['YAMAZAKI', 'SHAHBAZ'],
        rixoPrice: ['¥6,000', '¥7,000'],
        venueId: ['T008288'],
        mappings: [
            {
                typeOfVehicle: 'CAR / BIG CAR',
                rixoCompany: 'YAMAZAKI',
                rixoPrice: '¥6,000',
                venueId: 'T008288',
                stockLocation: 'GLOBAL KAWASAKI'
            },
            {
                typeOfVehicle: 'CAR / BIG CAR',
                rixoCompany: 'SHAHBAZ',
                rixoPrice: '¥7,000',
                venueId: 'T008288',
                stockLocation: 'GLOBAL KAWASAKI'
            },
            {
                typeOfVehicle: 'TRUCK',
                rixoCompany: 'SHAHBAZ',
                rixoPrice: '',
                venueId: 'T008288',
                stockLocation: 'GLOBAL KAWASAKI'
            },
        ]
    },
    "HAA KOBE": {
        typeOfVehicle: [],
        stockLocation: ['KLC', 'ECL KOBE'],
        rixoCompany: ['KLC'],
        rixoPrice: ['¥5,500', '¥4,500'],
        venueId: ['E0483'],
        mappings: [
            {
                rixoCompany: 'KLC',
                rixoPrice: '¥5,500',
                venueId: 'E0483',
                stockLocation: 'KLC',
                pol: 'OSAKA;SENBOKU;KOBE'
            },
            {
                rixoCompany: 'KLC',
                rixoPrice: '¥4,500',
                venueId: 'E0483',
                stockLocation: 'KLC',
                pol: 'OSAKA;SENBOKU;KOBE'
            },
            {
                rixoCompany: 'KLC',
                rixoPrice: '¥5,500',
                venueId: 'E0483',
                stockLocation: 'ECL KOBE',
                pol: 'OSAKA;SENBOKU;KOBE'
            },
        ]
    },
    "HERO": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL KAWASAKI'],
        rixoCompany: ['YAMAZAKI'],
        rixoPrice: ['¥10,000'],
        venueId: ['30617'],
        mappings: [
            {
                rixoCompany: 'YAMAZAKI',
                rixoPrice: '¥10,000',
                venueId: '30617',
                stockLocation: 'GLOBAL KAWASAKI'
            },
        ]
    },
    "HONDA HOKKAIDO": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL KAWASAKI'],
        rixoCompany: ['LOGICO'],
        rixoPrice: ['¥33,600'],
        venueId: ['1355400'],
        mappings: [
            {
                rixoCompany: 'LOGICO',
                rixoPrice: '¥33,600',
                venueId: '1355400',
                stockLocation: 'GLOBAL KAWASAKI'
            },
        ]
    },
    "HONDA KANSAI": {
        typeOfVehicle: [],
        stockLocation: ['KLC'],
        rixoCompany: ['KLC'],
        rixoPrice: ['¥5,500'],
        venueId: ['1355400'],
        mappings: [
            {
                rixoCompany: 'KLC',
                rixoPrice: '¥5,500',
                venueId: '1355400',
                stockLocation: 'KLC'
            },
        ]
    },
    "HONDA KYUSHU": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL HAKATA'],
        rixoCompany: ["Y'S"],
        rixoPrice: ['¥6,820'],
        venueId: ['1355400'],
        mappings: [
            {
                rixoCompany: "Y'S",
                rixoPrice: '¥6,820',
                venueId: '1355400',
                stockLocation: 'GLOBAL HAKATA'
            },
        ]
    },
    "HONDA NAGOYA": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL NAGOYA'],
        rixoCompany: ['STYLISH AUTO'],
        rixoPrice: ['¥5,000'],
        venueId: ['1355400'],
        mappings: [
            {
                rixoCompany: 'STYLISH AUTO',
                rixoPrice: '¥5,000',
                venueId: '1355400',
                stockLocation: 'GLOBAL NAGOYA'
            },
        ]
    },
    "HONDA SENDAI": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL KAWASAKI'],
        rixoCompany: ['LOGICO'],
        rixoPrice: ['¥19,800'],
        venueId: ['1355400'],
        mappings: [
            {
                rixoCompany: 'LOGICO',
                rixoPrice: '¥19,800',
                venueId: '1355400',
                stockLocation: 'GLOBAL KAWASAKI'
            },
        ]
    },
    "HONDA TOKYO": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL KAWASAKI'],
        rixoCompany: ['YAMAZAKI'],
        rixoPrice: ['¥8,000'],
        venueId: ['1355400'],
        mappings: [
            {
                rixoCompany: 'YAMAZAKI',
                rixoPrice: '¥8,000',
                venueId: '1355400',
                stockLocation: 'GLOBAL KAWASAKI'
            },
        ]
    },
    "IAA OSAKA": {
        typeOfVehicle: [],
        stockLocation: ['KLC'],
        rixoCompany: ['KLC'],
        rixoPrice: ['¥3,800'],
        venueId: ['27791'],
        mappings: [
            {
                rixoCompany: 'KLC',
                rixoPrice: '¥3,800',
                venueId: '27791',
                stockLocation: 'KLC'
            },
        ]
    },
    "ISUZU KOBE": {
        typeOfVehicle: [],
        stockLocation: ['KLC'],
        rixoCompany: ['KLC'],
        rixoPrice: ['¥14,000'],
        venueId: ['A052166'],
        mappings: [
            {
                rixoCompany: 'KLC',
                rixoPrice: '¥14,000',
                venueId: 'A052166',
                stockLocation: 'KLC'
            },
        ]
    },
    "ISUZU KOBE (SAKURAI YARD)": {
        typeOfVehicle: [],
        stockLocation: ['KLC'],
        rixoCompany: ['KLC'],
        rixoPrice: ['¥28,000'],
        venueId: ['A052166'],
        mappings: [
            {
                rixoCompany: 'KLC',
                rixoPrice: '¥28,000',
                venueId: 'A052166',
                stockLocation: 'KLC'
            },
        ]
    },
    "ISUZU KYUSHU": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL HAKATA'],
        rixoCompany: ["Y'S"],
        rixoPrice: ['¥4400 | ¥7700'],
        venueId: ['A052166'],
        mappings: [
            {
                rixoCompany: "Y'S",
                rixoPrice: '¥4400 | ¥7700',
                venueId: 'A052166',
                stockLocation: 'GLOBAL HAKATA'
            },
        ]
    },
    "ISUZU TOKYO": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL KAWASAKI'],
        rixoCompany: ['-'],
        rixoPrice: [],
        venueId: ['A052166'],
        mappings: [
            {
                rixoCompany: '-',
                venueId: 'A052166',
                stockLocation: 'GLOBAL KAWASAKI'
            },
        ]
    },
    "JAA": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL KAWASAKI'],
        rixoCompany: ['YAMAZAKI', 'SHAHBAZ'],
        rixoPrice: ['¥6,000'],
        venueId: ['11390'],
        mappings: [
            {
                rixoCompany: 'YAMAZAKI',
                rixoPrice: '¥6,000',
                venueId: '11390',
                stockLocation: 'GLOBAL KAWASAKI'
            },
            {
                rixoCompany: 'SHAHBAZ',
                rixoPrice: '¥6,000',
                venueId: '11390',
                stockLocation: 'GLOBAL KAWASAKI'
            },
        ]
    },
    "JU AICHI": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL NAGOYA'],
        rixoCompany: ['STYLISH AUTO'],
        rixoPrice: ['¥4,000'],
        venueId: ['95518'],
        mappings: [
            {
                rixoCompany: 'STYLISH AUTO',
                rixoPrice: '¥4,000',
                venueId: '95518',
                stockLocation: 'GLOBAL NAGOYA'
            },
        ]
    },
    "JU AOMORI": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL KAWASAKI'],
        rixoCompany: ['LOGICO'],
        rixoPrice: ['¥52,400'],
        venueId: ['200539'],
        mappings: [
            {
                rixoCompany: 'LOGICO',
                rixoPrice: '¥52,400',
                venueId: '200539',
                stockLocation: 'GLOBAL KAWASAKI'
            },
        ]
    },
    "JU CHIBA": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL KAWASAKI'],
        rixoCompany: ['YAMAZAKI', 'SHAHBAZ'],
        rixoPrice: ['¥8,000', '¥7,000'],
        venueId: ['59077'],
        mappings: [
            {
                rixoCompany: 'YAMAZAKI',
                rixoPrice: '¥8,000',
                venueId: '59077',
                stockLocation: 'GLOBAL KAWASAKI'
            },
            {
                rixoCompany: 'SHAHBAZ',
                rixoPrice: '¥7,000',
                venueId: '59077',
                stockLocation: 'GLOBAL KAWASAKI'
            },
        ]
    },
    "JU FUKUI": {
        typeOfVehicle: [],
        stockLocation: ['-'],
        rixoCompany: ['-'],
        rixoPrice: [],
        venueId: ['126589'],
        mappings: [
            {
                rixoCompany: '-',
                venueId: '126589',
                stockLocation: '-'
            },
        ]
    },
    "JU FUKUOKA": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL HAKATA'],
        rixoCompany: ["Y'S"],
        rixoPrice: ['¥3,900'],
        venueId: ['37488'],
        mappings: [
            {
                rixoCompany: "Y'S",
                rixoPrice: '¥3,900',
                venueId: '37488',
                stockLocation: 'GLOBAL HAKATA'
            },
        ]
    },
    "JU FUKUSHIMA": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL KAWASAKI'],
        rixoCompany: ['LOGICO'],
        rixoPrice: ['¥18,000'],
        venueId: ['88472'],
        mappings: [
            {
                rixoCompany: 'LOGICO',
                rixoPrice: '¥18,000',
                venueId: '88472',
                stockLocation: 'GLOBAL KAWASAKI'
            },
        ]
    },
    "JU GIFU": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL NAGOYA'],
        rixoCompany: ['STYLISH AUTO'],
        rixoPrice: ['¥7,500'],
        venueId: ['50354'],
        mappings: [
            {
                rixoCompany: 'STYLISH AUTO',
                rixoPrice: '¥7,500',
                venueId: '50354',
                stockLocation: 'GLOBAL NAGOYA'
            },
        ]
    },
    "JU GUNMA": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL KAWASAKI'],
        rixoCompany: ['LOGICO'],
        rixoPrice: ['¥12,000'],
        venueId: ['700485'],
        mappings: [
            {
                rixoCompany: 'LOGICO',
                rixoPrice: '¥12,000',
                venueId: '700485',
                stockLocation: 'GLOBAL KAWASAKI'
            },
        ]
    },
    "JU HIROSHIMA": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL HAKATA'],
        rixoCompany: ["Y'S"],
        rixoPrice: ['¥14,300'],
        venueId: ['77510'],
        mappings: [
            {
                rixoCompany: "Y'S",
                rixoPrice: '¥14,300',
                venueId: '77510',
                stockLocation: 'GLOBAL HAKATA'
            },
        ]
    },
    "JU HOKKAIDO": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL KAWASAKI'],
        rixoCompany: ['LOGICO'],
        rixoPrice: ['¥34,600'],
        venueId: ['63257'],
        mappings: [
            {
                rixoCompany: 'LOGICO',
                rixoPrice: '¥34,600',
                venueId: '63257',
                stockLocation: 'GLOBAL KAWASAKI'
            },
        ]
    },
    "JU IBARAKI": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL KAWASAKI'],
        rixoCompany: ['LOGICO'],
        rixoPrice: ['¥14,400'],
        venueId: ['80548'],
        mappings: [
            {
                rixoCompany: 'LOGICO',
                rixoPrice: '¥14,400',
                venueId: '80548',
                stockLocation: 'GLOBAL KAWASAKI'
            },
        ]
    },
    "JU ISHIKAWA": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL NAGOYA'],
        rixoCompany: ['LOGICO'],
        rixoPrice: ['¥16,600'],
        venueId: ['70496'],
        mappings: [
            {
                rixoCompany: 'LOGICO',
                rixoPrice: '¥16,600',
                venueId: '70496',
                stockLocation: 'GLOBAL NAGOYA'
            },
        ]
    },
    "JU KANAGAWA": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL KAWASAKI'],
        rixoCompany: ['LOGICO', 'YAMAZAKI'],
        rixoPrice: ['¥12,000'],
        venueId: ['90471'],
        mappings: [
            {
                rixoCompany: 'LOGICO',
                rixoPrice: '¥12,000',
                venueId: '90471',
                stockLocation: 'GLOBAL KAWASAKI'
            },
            {
                rixoCompany: 'YAMAZAKI',
                venueId: '90471',
                stockLocation: 'GLOBAL KAWASAKI'
            },
        ]
    },
    "JU KUMAMOTO": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL HAKATA'],
        rixoCompany: ["Y'S"],
        rixoPrice: ['¥12,100'],
        venueId: ['70513'],
        mappings: [
            {
                rixoCompany: "Y'S",
                rixoPrice: '¥12,100',
                venueId: '70513',
                stockLocation: 'GLOBAL HAKATA'
            },
        ]
    },
    "JU MIE": {
        typeOfVehicle: [],
        stockLocation: ['KLC'],
        rixoCompany: ['KLC'],
        rixoPrice: [],
        venueId: ['316009'],
        mappings: [
            {
                rixoCompany: 'KLC',
                venueId: '316009',
                stockLocation: 'KLC'
            },
        ]
    },
    "JU MIYAGI": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL KAWASAKI'],
        rixoCompany: ['LOGICO'],
        rixoPrice: ['¥19,400'],
        venueId: ['70506'],
        mappings: [
            {
                rixoCompany: 'LOGICO',
                rixoPrice: '¥19,400',
                venueId: '70506',
                stockLocation: 'GLOBAL KAWASAKI'
            },
        ]
    },
    "JU MIYAZAKI": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL HAKATA'],
        rixoCompany: ["Y'S"],
        rixoPrice: [],
        venueId: ['70519'],
        mappings: [
            {
                rixoCompany: "Y'S",
                venueId: '70519',
                stockLocation: 'GLOBAL HAKATA'
            },
        ]
    },
    "JU NAGANO": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL KAWASAKI'],
        rixoCompany: ['LOGICO'],
        rixoPrice: ['¥26,400'],
        venueId: ['80536'],
        mappings: [
            {
                rixoCompany: 'LOGICO',
                rixoPrice: '¥26,400',
                venueId: '80536',
                stockLocation: 'GLOBAL KAWASAKI'
            },
        ]
    },
    "JU NAGASAKI": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL HAKATA'],
        rixoCompany: ["Y'S"],
        rixoPrice: [],
        venueId: ['A052166'],
        mappings: [
            {
                rixoCompany: "Y'S",
                venueId: 'A052166',
                stockLocation: 'GLOBAL HAKATA'
            },
        ]
    },
    "JU NARA": {
        typeOfVehicle: [],
        stockLocation: ['KLC'],
        rixoCompany: ['KLC'],
        rixoPrice: ['¥9,500'],
        venueId: ['130528'],
        mappings: [
            {
                rixoCompany: 'KLC',
                rixoPrice: '¥9,500',
                venueId: '130528',
                stockLocation: 'KLC'
            },
        ]
    },
    "JU NIIGATA": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL KAWASAKI'],
        rixoCompany: ['LOGICO'],
        rixoPrice: ['¥22,200'],
        venueId: ['45548'],
        mappings: [
            {
                rixoCompany: 'LOGICO',
                rixoPrice: '¥22,200',
                venueId: '45548',
                stockLocation: 'GLOBAL KAWASAKI'
            },
        ]
    },
    "JU OITA": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL HAKATA'],
        rixoCompany: ["Y'S"],
        rixoPrice: [],
        venueId: ['45522'],
        mappings: [
            {
                rixoCompany: "Y'S",
                venueId: '45522',
                stockLocation: 'GLOBAL HAKATA'
            },
        ]
    },
    "JU OKINAWA": {
        typeOfVehicle: [],
        stockLocation: ['-'],
        rixoCompany: ['-'],
        rixoPrice: [],
        venueId: ['53143'],
        mappings: [
            {
                rixoCompany: '-',
                venueId: '53143',
                stockLocation: '-'
            },
        ]
    },
    "JU SAITAMA": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL KAWASAKI'],
        rixoCompany: ['YAMAZAKI', 'SHAHBAZ'],
        rixoPrice: ['¥7,000'],
        venueId: ['16845'],
        mappings: [
            {
                rixoCompany: 'YAMAZAKI',
                rixoPrice: '¥7,000',
                venueId: '16845',
                stockLocation: 'GLOBAL KAWASAKI'
            },
            {
                rixoCompany: 'SHAHBAZ',
                rixoPrice: '¥7,000',
                venueId: '16845',
                stockLocation: 'GLOBAL KAWASAKI'
            },
        ]
    },
    "JU SHIMANE": {
        typeOfVehicle: [],
        stockLocation: ['-'],
        rixoCompany: ['-'],
        rixoPrice: [],
        venueId: ['A052166'],
        mappings: [
            {
                rixoCompany: '-',
                venueId: 'A052166',
                stockLocation: '-'
            },
        ]
    },
    "JU SHIZUOKA": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL NAGOYA'],
        rixoCompany: ['STYLISH AUTO'],
        rixoPrice: ['¥14,000'],
        venueId: ['900513'],
        mappings: [
            {
                rixoCompany: 'STYLISH AUTO',
                rixoPrice: '¥14,000',
                venueId: '900513',
                stockLocation: 'GLOBAL NAGOYA'
            },
        ]
    },
    "JU TOCHIGI": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL KAWASAKI'],
        rixoCompany: ['LOGICO'],
        rixoPrice: ['¥13,200'],
        venueId: ['80521'],
        mappings: [
            {
                rixoCompany: 'LOGICO',
                rixoPrice: '¥13,200',
                venueId: '80521',
                stockLocation: 'GLOBAL KAWASAKI'
            },
        ]
    },
    "JU TOKYO": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL KAWASAKI'],
        rixoCompany: ['YAMAZAKI'],
        rixoPrice: ['¥6,000'],
        venueId: ['20558'],
        mappings: [
            {
                rixoCompany: 'YAMAZAKI',
                rixoPrice: '¥6,000',
                venueId: '20558',
                stockLocation: 'GLOBAL KAWASAKI'
            },
        ]
    },
    "JU TOYAMA": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL NAGOYA'],
        rixoCompany: ['STYLISH AUTO', 'LOGICO'],
        rixoPrice: ['¥18,100'],
        venueId: ['600525'],
        mappings: [
            {
                rixoCompany: 'STYLISH AUTO',
                venueId: '600525',
                stockLocation: 'GLOBAL NAGOYA'
            },
            {
                rixoCompany: 'LOGICO',
                rixoPrice: '¥18,100',
                venueId: '600525',
                stockLocation: 'GLOBAL NAGOYA'
            },
        ]
    },
    "JU YAMAGATA": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL KAWASAKI'],
        rixoCompany: ['LOGICO'],
        rixoPrice: [],
        venueId: ['90532'],
        mappings: [
            {
                rixoCompany: 'LOGICO',
                venueId: '90532',
                stockLocation: 'GLOBAL KAWASAKI'
            },
        ]
    },
    "JU YAMAGUCHI": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL HAKATA'],
        rixoCompany: ["Y'S"],
        rixoPrice: [],
        venueId: ['59160'],
        mappings: [
            {
                rixoCompany: "Y'S",
                venueId: '59160',
                stockLocation: 'GLOBAL HAKATA'
            },
        ]
    },
    "JU YAMANASHI": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL KAWASAKI'],
        rixoCompany: ['LOGICO'],
        rixoPrice: ['¥21,600'],
        venueId: ['300541'],
        mappings: [
            {
                rixoCompany: 'LOGICO',
                rixoPrice: '¥21,600',
                venueId: '300541',
                stockLocation: 'GLOBAL KAWASAKI'
            },
        ]
    },
    "KCAA FUKUOKA": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL HAKATA'],
        rixoCompany: ["Y'S"],
        rixoPrice: ['¥3,900'],
        venueId: ['J2671'],
        mappings: [
            {
                rixoCompany: "Y'S",
                rixoPrice: '¥3,900',
                venueId: 'J2671',
                stockLocation: 'GLOBAL HAKATA'
            },
        ]
    },
    "KCAA KYOTO": {
        typeOfVehicle: [],
        stockLocation: ['KLC'],
        rixoCompany: ['KLC'],
        rixoPrice: ['¥9,500'],
        venueId: ['J2671'],
        mappings: [
            {
                rixoCompany: 'KLC',
                rixoPrice: '¥9,500',
                venueId: 'J2671',
                stockLocation: 'KLC'
            },
        ]
    },
    "KCAA MINAMI KYUSHU": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL HAKATA'],
        rixoCompany: ["Y'S"],
        rixoPrice: ['¥14,300'],
        venueId: ['J2671'],
        mappings: [
            {
                rixoCompany: "Y'S",
                rixoPrice: '¥14,300',
                venueId: 'J2671',
                stockLocation: 'GLOBAL HAKATA'
            },
        ]
    },
    "KCAA YAMAGUCHI": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL HAKATA'],
        rixoCompany: ["Y'S"],
        rixoPrice: ['¥10,200'],
        venueId: ['J2671'],
        mappings: [
            {
                rixoCompany: "Y'S",
                rixoPrice: '¥10,200',
                venueId: 'J2671',
                stockLocation: 'GLOBAL HAKATA'
            },
        ]
    },
    "LAA OKAYAMA": {
        typeOfVehicle: [],
        stockLocation: ['KLC'],
        rixoCompany: ['KLC'],
        rixoPrice: ['¥10,800'],
        venueId: ['00S7784'],
        mappings: [
            {
                rixoCompany: 'KLC',
                rixoPrice: '¥10,800',
                venueId: '00S7784',
                stockLocation: 'KLC'
            },
        ]
    },
    "LAA SHIKOKU": {
        typeOfVehicle: [],
        stockLocation: ['KLC'],
        rixoCompany: ['KLC'],
        rixoPrice: ['¥15,000'],
        venueId: ['00S7784'],
        mappings: [
            {
                rixoCompany: 'KLC',
                rixoPrice: '¥15,000',
                venueId: '00S7784',
                stockLocation: 'KLC'
            },
        ]
    },
    "LUM FUKUOKA": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL HAKATA'],
        rixoCompany: ['LOGICO'],
        rixoPrice: ['¥6,200'],
        venueId: ['1564'],
        mappings: [
            {
                rixoCompany: 'LOGICO',
                rixoPrice: '¥6,200',
                venueId: '1564',
                stockLocation: 'GLOBAL HAKATA'
            },
        ]
    },
    "LUM HOKKAIDO": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL KAWASAKI'],
        rixoCompany: ['LOGICO'],
        rixoPrice: ['¥33,600'],
        venueId: ['1564'],
        mappings: [
            {
                rixoCompany: 'LOGICO',
                rixoPrice: '¥33,600',
                venueId: '1564',
                stockLocation: 'GLOBAL KAWASAKI'
            },
        ]
    },
    "LUM KOBE": {
        typeOfVehicle: [],
        stockLocation: ['KLC'],
        rixoCompany: ['LOGICO'],
        rixoPrice: ['¥6,500'],
        venueId: ['1564'],
        mappings: [
            {
                rixoCompany: 'LOGICO',
                rixoPrice: '¥6,500',
                venueId: '1564',
                stockLocation: 'KLC'
            },
        ]
    },
    "LUM KOBE (HIROSHIMA)": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL HAKATA'],
        rixoCompany: ['LOGICO'],
        rixoPrice: ['¥30,600'],
        venueId: ['1564'],
        mappings: [
            {
                rixoCompany: 'LOGICO',
                rixoPrice: '¥30,600',
                venueId: '1564',
                stockLocation: 'GLOBAL HAKATA'
            },
        ]
    },
    "LUM NAGOYA": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL NAGOYA'],
        rixoCompany: ['LOGICO'],
        rixoPrice: ['¥6,000'],
        venueId: ['1564'],
        mappings: [
            {
                rixoCompany: 'LOGICO',
                rixoPrice: '¥6,000',
                venueId: '1564',
                stockLocation: 'GLOBAL NAGOYA'
            },
        ]
    },
    "LUM TOKYO": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL KAWASAKI'],
        rixoCompany: ['LOGICO'],
        rixoPrice: ['¥9,600'],
        venueId: ['1564'],
        mappings: [
            {
                rixoCompany: 'LOGICO',
                rixoPrice: '¥9,600',
                venueId: '1564',
                stockLocation: 'GLOBAL KAWASAKI'
            },
        ]
    },
    "LUM TOKYO (SENDAI)": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL KAWASAKI'],
        rixoCompany: ['LOGICO'],
        rixoPrice: ['¥19,800'],
        venueId: ['1564'],
        mappings: [
            {
                rixoCompany: 'LOGICO',
                rixoPrice: '¥19,800',
                venueId: '1564',
                stockLocation: 'GLOBAL KAWASAKI'
            },
        ]
    },
    "MIRIVE AICHI": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL NAGOYA'],
        rixoCompany: ['STYLISH AUTO'],
        rixoPrice: ['¥6,000'],
        venueId: ['710596'],
        mappings: [
            {
                rixoCompany: 'STYLISH AUTO',
                rixoPrice: '¥6,000',
                venueId: '710596',
                stockLocation: 'GLOBAL NAGOYA'
            },
        ]
    },
    "MIRIVE OSAKA": {
        typeOfVehicle: [],
        stockLocation: ['KLC'],
        rixoCompany: ['KLC'],
        rixoPrice: ['¥6,000'],
        venueId: ['710596'],
        mappings: [
            {
                rixoCompany: 'KLC',
                rixoPrice: '¥6,000',
                venueId: '710596',
                stockLocation: 'KLC'
            },
        ]
    },
    "MIRIVE SAITAMA": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL KAWASAKI', 'AQUA LOGISTICS'],
        rixoCompany: ['LOGICO'],
        rixoPrice: ['¥10,800', '¥19,600'],
        venueId: ['710596'],
        mappings: [
            {
                rixoCompany: 'LOGICO',
                rixoPrice: '¥10,800',
                venueId: '710596',
                stockLocation: 'GLOBAL KAWASAKI'
            },
            {
                rixoCompany: 'LOGICO',
                rixoPrice: '¥19,600',
                venueId: '710596',
                stockLocation: 'GLOBAL KAWASAKI'
            },
        ]
    },
    "NAA FUKUOKA": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL HAKATA'],
        rixoCompany: ["Y'S"],
        rixoPrice: ['¥3,900'],
        venueId: ['9733100'],
        mappings: [
            {
                rixoCompany: "Y'S",
                rixoPrice: '¥3,900',
                venueId: '9733100',
                stockLocation: 'GLOBAL HAKATA'
            },
        ]
    },
    "NAA NAGOYA": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL NAGOYA'],
        rixoCompany: ['STYLISH AUTO'],
        rixoPrice: ['¥7,000'],
        venueId: ['9733100'],
        mappings: [
            {
                rixoCompany: 'STYLISH AUTO',
                rixoPrice: '¥7,000',
                venueId: '9733100',
                stockLocation: 'GLOBAL NAGOYA'
            },
        ]
    },
    "NAA NAGOYA (HOKURIKU)": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL NAGOYA'],
        rixoCompany: ['STYLISH AUTO', 'HIDA'],
        rixoPrice: ['¥28,000', '¥15,000'],
        venueId: ['9733100'],
        mappings: [
            {
                rixoCompany: 'STYLISH AUTO',
                rixoPrice: '¥28,000',
                venueId: '9733100',
                stockLocation: 'GLOBAL NAGOYA'
            },
            {
                rixoCompany: 'HIDA',
                rixoPrice: '¥15,000',
                venueId: '9733100',
                stockLocation: 'GLOBAL NAGOYA'
            },
        ]
    },
    "NAA OSAKA": {
        typeOfVehicle: [],
        stockLocation: ['KLC'],
        rixoCompany: ['KLC'],
        rixoPrice: ['¥5,500'],
        venueId: ['9733100'],
        mappings: [
            {
                rixoCompany: 'KLC',
                rixoPrice: '¥5,500',
                venueId: '9733100',
                stockLocation: 'KLC'
            },
        ]
    },
    "NAA TOKYO": {
        typeOfVehicle: ['CAR'],
        stockLocation: ['GLOBAL KAWASAKI'],
        rixoCompany: ['YAMAZAKI', 'SHAHBAZ'],
        rixoPrice: ['¥4,000', '¥3,500'],
        venueId: ['9733100'],
        mappings: [
            {
                typeOfVehicle: 'CAR',
                rixoCompany: 'YAMAZAKI',
                rixoPrice: '¥4,000',
                venueId: '9733100',
                stockLocation: 'GLOBAL KAWASAKI'
            },
            {
                typeOfVehicle: 'CAR',
                rixoCompany: 'SHAHBAZ',
                rixoPrice: '¥3,500',
                venueId: '9733100',
                stockLocation: 'GLOBAL KAWASAKI'
            },
        ]
    },
    "NOAA": {
        typeOfVehicle: [],
        stockLocation: ['KLC'],
        rixoCompany: ['KLC'],
        rixoPrice: ['¥5,500'],
        venueId: ['Z289700'],
        mappings: [
            {
                rixoCompany: 'KLC',
                rixoPrice: '¥5,500',
                venueId: 'Z289700',
                stockLocation: 'KLC'
            },
        ]
    },
    "NPS FUKUOKA": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL HAKATA'],
        rixoCompany: ["Y'S"],
        rixoPrice: [],
        venueId: ['7378'],
        mappings: [
            {
                rixoCompany: "Y'S",
                venueId: '7378',
                stockLocation: 'GLOBAL HAKATA'
            },
        ]
    },
    "NPS OSAKA": {
        typeOfVehicle: [],
        stockLocation: ['KLC'],
        rixoCompany: ['KLC'],
        rixoPrice: ['¥3,800'],
        venueId: ['7378'],
        mappings: [
            {
                rixoCompany: 'KLC',
                rixoPrice: '¥3,800',
                venueId: '7378',
                stockLocation: 'KLC'
            },
        ]
    },
    "NPS SENDAI": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL KAWASAKI'],
        rixoCompany: ['LOGICO'],
        rixoPrice: ['¥19,800'],
        venueId: ['7378'],
        mappings: [
            {
                rixoCompany: 'LOGICO',
                rixoPrice: '¥19,800',
                venueId: '7378',
                stockLocation: 'GLOBAL KAWASAKI'
            },
        ]
    },
    "NPS TOCHIGI": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL KAWASAKI'],
        rixoCompany: ['LOGICO'],
        rixoPrice: ['¥13,200'],
        venueId: ['7378'],
        mappings: [
            {
                rixoCompany: 'LOGICO',
                rixoPrice: '¥13,200',
                venueId: '7378',
                stockLocation: 'GLOBAL KAWASAKI'
            },
        ]
    },
    "NPS TOKYO": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL KAWASAKI'],
        rixoCompany: ['YAMAZAKI'],
        rixoPrice: ['¥7,000'],
        venueId: ['7378'],
        mappings: [
            {
                rixoCompany: 'YAMAZAKI',
                rixoPrice: '¥7,000',
                venueId: '7378',
                stockLocation: 'GLOBAL KAWASAKI'
            },
        ]
    },
    "NPS TOMAKOMAI": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL KAWASAKI'],
        rixoCompany: ['LOGICO'],
        rixoPrice: [],
        venueId: ['7378'],
        mappings: [
            {
                rixoCompany: 'LOGICO',
                venueId: '7378',
                stockLocation: 'GLOBAL KAWASAKI'
            },
        ]
    },
    "ORIX ATSUGI": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL KAWASAKI'],
        rixoCompany: ['LOGICO'],
        rixoPrice: ['¥8,900'],
        venueId: ['50000052'],
        mappings: [
            {
                rixoCompany: 'LOGICO',
                rixoPrice: '¥8,900',
                venueId: '50000052',
                stockLocation: 'GLOBAL KAWASAKI'
            },
        ]
    },
    "ORIX ATSUGI (OYAMA)": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL KAWASAKI'],
        rixoCompany: ['LOGICO'],
        rixoPrice: ['¥13,200'],
        venueId: ['50000052'],
        mappings: [
            {
                rixoCompany: 'LOGICO',
                rixoPrice: '¥13,200',
                venueId: '50000052',
                stockLocation: 'GLOBAL KAWASAKI'
            },
        ]
    },
    "ORIX FUKUOKA": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL HAKATA'],
        rixoCompany: ["Y'S"],
        rixoPrice: ['¥3,900'],
        venueId: ['50000052'],
        mappings: [
            {
                rixoCompany: "Y'S",
                rixoPrice: '¥3,900',
                venueId: '50000052',
                stockLocation: 'GLOBAL HAKATA'
            },
        ]
    },
    "ORIX KOBE": {
        typeOfVehicle: [],
        stockLocation: ['KLC'],
        rixoCompany: ['KLC'],
        rixoPrice: ['¥5,500'],
        venueId: ['50000052'],
        mappings: [
            {
                rixoCompany: 'KLC',
                rixoPrice: '¥5,500',
                venueId: '50000052',
                stockLocation: 'KLC'
            },
        ]
    },
    "ORIX SENDAI": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL KAWASAKI'],
        rixoCompany: ['LOGICO'],
        rixoPrice: ['¥19,800'],
        venueId: ['50000052'],
        mappings: [
            {
                rixoCompany: 'LOGICO',
                rixoPrice: '¥19,800',
                venueId: '50000052',
                stockLocation: 'GLOBAL KAWASAKI'
            },
        ]
    },
    "SAA HAMAMATSU": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL NAGOYA'],
        rixoCompany: ['STYLISH AUTO'],
        rixoPrice: ['¥10,000'],
        venueId: ['3732'],
        mappings: [
            {
                rixoCompany: 'STYLISH AUTO',
                rixoPrice: '¥10,000',
                venueId: '3732',
                stockLocation: 'GLOBAL NAGOYA'
            },
        ]
    },
    "SAA SAPPORO": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL KAWASAKI'],
        rixoCompany: ['LOGICO'],
        rixoPrice: ['¥34,600'],
        venueId: ['57455'],
        mappings: [
            {
                rixoCompany: 'LOGICO',
                rixoPrice: '¥34,600',
                venueId: '57455',
                stockLocation: 'GLOBAL KAWASAKI'
            },
        ]
    },
    "TAA CHUBU": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL NAGOYA'],
        rixoCompany: ['STYLISH AUTO'],
        rixoPrice: ['¥5,000'],
        venueId: ['65010'],
        mappings: [
            {
                rixoCompany: 'STYLISH AUTO',
                rixoPrice: '¥5,000',
                venueId: '65010',
                stockLocation: 'GLOBAL NAGOYA'
            },
        ]
    },
    "TAA CHUBU (HOKURIKU)": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL NAGOYA'],
        rixoCompany: ['STYLISH AUTO', 'LOGICO'],
        rixoPrice: ['¥18,000', '¥16,600'],
        venueId: ['65010'],
        mappings: [
            {
                rixoCompany: 'STYLISH AUTO',
                rixoPrice: '¥18,000',
                venueId: '65010',
                stockLocation: 'GLOBAL NAGOYA'
            },
            {
                rixoCompany: 'LOGICO',
                rixoPrice: '¥16,600',
                venueId: '65010',
                stockLocation: 'GLOBAL NAGOYA'
            },
        ]
    },
    "TAA CHUBU (SHIZUOKA)": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL NAGOYA'],
        rixoCompany: ['STYLISH AUTO'],
        rixoPrice: ['¥13,000'],
        venueId: ['65010'],
        mappings: [
            {
                rixoCompany: 'STYLISH AUTO',
                rixoPrice: '¥13,000',
                venueId: '65010',
                stockLocation: 'GLOBAL NAGOYA'
            },
        ]
    },
    "TAA HIROSHIMA": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL HAKATA'],
        rixoCompany: ["Y'S"],
        rixoPrice: ['¥12,000'],
        venueId: ['65010'],
        mappings: [
            {
                rixoCompany: "Y'S",
                rixoPrice: '¥12,000',
                venueId: '65010',
                stockLocation: 'GLOBAL HAKATA'
            },
        ]
    },
    "TAA HOKKAIDO": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL KAWASAKI'],
        rixoCompany: ['LOGICO'],
        rixoPrice: ['¥34,600'],
        venueId: ['65010'],
        mappings: [
            {
                rixoCompany: 'LOGICO',
                rixoPrice: '¥34,600',
                venueId: '65010',
                stockLocation: 'GLOBAL KAWASAKI'
            },
        ]
    },
    "TAA HYOGO": {
        typeOfVehicle: [],
        stockLocation: ['KLC'],
        rixoCompany: ['KLC'],
        rixoPrice: ['¥5,500'],
        venueId: ['65010'],
        mappings: [
            {
                rixoCompany: 'KLC',
                rixoPrice: '¥5,500',
                venueId: '65010',
                stockLocation: 'KLC'
            },
        ]
    },
    "TAA KANTO": {
        typeOfVehicle: ['CAR', 'BIG CAR/TRUCK'],
        stockLocation: ['GLOBAL KAWASAKI'],
        rixoCompany: ['YAMAZAKI', 'SHAHBAZ'],
        rixoPrice: ['¥6,000', '¥8,000'],
        venueId: ['65010'],
        mappings: [
            {
                rixoCompany: 'YAMAZAKI',
                rixoPrice: '¥6,000',
                venueId: '65010',
                stockLocation: 'GLOBAL KAWASAKI'
            },
            {
                typeOfVehicle: 'CAR',
                rixoCompany: 'SHAHBAZ',
                rixoPrice: '¥6,000',
                venueId: '65010',
                stockLocation: 'GLOBAL KAWASAKI'
            },
            {
                typeOfVehicle: 'BIG CAR/TRUCK',
                rixoCompany: 'SHAHBAZ',
                rixoPrice: '¥8,000',
                venueId: '65010',
                stockLocation: 'GLOBAL KAWASAKI'
            },
        ]
    },
    "TAA KANTO (KITA KANTO)": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL KAWASAKI'],
        rixoCompany: ['LOGICO'],
        rixoPrice: ['¥12,000'],
        venueId: ['65010'],
        mappings: [
            {
                rixoCompany: 'LOGICO',
                rixoPrice: '¥12,000',
                venueId: '65010',
                stockLocation: 'GLOBAL KAWASAKI'
            },
        ]
    },
    "TAA KANTO (SAITAMA)": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL KAWASAKI'],
        rixoCompany: ['YAMAZAKI', 'SHAHBAZ'],
        rixoPrice: ['¥7,000', '¥8,000'],
        venueId: ['65010'],
        mappings: [
            {
                rixoCompany: 'YAMAZAKI',
                rixoPrice: '¥7,000',
                venueId: '65010',
                stockLocation: 'GLOBAL KAWASAKI'
            },
            {
                rixoCompany: 'SHAHBAZ',
                rixoPrice: '¥8,000',
                venueId: '65010',
                stockLocation: 'GLOBAL KAWASAKI'
            },
        ]
    },
    "TAA KANTO (TAMA)": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL KAWASAKI'],
        rixoCompany: ['TAA'],
        rixoPrice: ['¥12,000'],
        venueId: ['65010'],
        mappings: [
            {
                rixoCompany: 'TAA',
                rixoPrice: '¥12,000',
                venueId: '65010',
                stockLocation: 'GLOBAL KAWASAKI'
            },
        ]
    },
    "TAA KINKI": {
        typeOfVehicle: [],
        stockLocation: ['KLC'],
        rixoCompany: ['KLC'],
        rixoPrice: ['¥9,500'],
        venueId: ['65010'],
        mappings: [
            {
                rixoCompany: 'KLC',
                rixoPrice: '¥9,500',
                venueId: '65010',
                stockLocation: 'KLC'
            },
        ]
    },
    "TAA KINKI (SHIGA YARD)": {
        typeOfVehicle: [],
        stockLocation: ['KLC'],
        rixoCompany: ['KLC'],
        rixoPrice: ['¥18,000'],
        venueId: ['65010'],
        mappings: [
            {
                rixoCompany: 'KLC',
                rixoPrice: '¥18,000',
                venueId: '65010',
                stockLocation: 'KLC'
            },
        ]
    },
    "TAA KYUSHU": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL HAKATA'],
        rixoCompany: ["Y'S"],
        rixoPrice: ['¥4,620'],
        venueId: ['65010'],
        mappings: [
            {
                rixoCompany: "Y'S",
                rixoPrice: '¥4,620',
                venueId: '65010',
                stockLocation: 'GLOBAL HAKATA'
            },
        ]
    },
    "TAA MINAMI KYUSHU": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL HAKATA'],
        rixoCompany: ["Y'S"],
        rixoPrice: ['¥15,400'],
        venueId: ['65010'],
        mappings: [
            {
                rixoCompany: "Y'S",
                rixoPrice: '¥15,400',
                venueId: '65010',
                stockLocation: 'GLOBAL HAKATA'
            },
        ]
    },
    "TAA SHIKOKU": {
        typeOfVehicle: [],
        stockLocation: ['KLC'],
        rixoCompany: ['KLC'],
        rixoPrice: ['¥15,000'],
        venueId: ['65010'],
        mappings: [
            {
                rixoCompany: 'KLC',
                rixoPrice: '¥15,000',
                venueId: '65010',
                stockLocation: 'KLC'
            },
        ]
    },
    "TAA SHIKOKU (EHIME)": {
        typeOfVehicle: [],
        stockLocation: ['KLC'],
        rixoCompany: ['TAA'],
        rixoPrice: ['¥20,000'],
        venueId: ['65010'],
        mappings: [
            {
                rixoCompany: 'TAA',
                rixoPrice: '¥20,000',
                venueId: '65010',
                stockLocation: 'KLC'
            },
        ]
    },
    "TAA TOHOKU": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL KAWASAKI'],
        rixoCompany: ['LOGICO'],
        rixoPrice: ['¥18,000'],
        venueId: ['65010'],
        mappings: [
            {
                rixoCompany: 'LOGICO',
                rixoPrice: '¥18,000',
                venueId: '65010',
                stockLocation: 'GLOBAL KAWASAKI'
            },
        ]
    },
    "TAA TOHOKU (MIYAGI)": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL KAWASAKI'],
        rixoCompany: ['LOGICO'],
        rixoPrice: ['¥19,800'],
        venueId: ['65010'],
        mappings: [
            {
                rixoCompany: 'LOGICO',
                rixoPrice: '¥19,800',
                venueId: '65010',
                stockLocation: 'GLOBAL KAWASAKI'
            },
        ]
    },
    "TAA YOKOHAMA": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL KAWASAKI'],
        rixoCompany: ['YAMAZAKI', 'SHAHBAZ'],
        rixoPrice: ['¥9,000', '¥3,500'],
        venueId: ['65010'],
        mappings: [
            {
                rixoCompany: 'YAMAZAKI',
                rixoPrice: '¥9,000',
                venueId: '65010',
                stockLocation: 'GLOBAL KAWASAKI'
            },
            {
                rixoCompany: 'SHAHBAZ',
                rixoPrice: '¥3,500',
                venueId: '65010',
                stockLocation: 'GLOBAL KAWASAKI'
            },
        ]
    },
    "TAA YOKOHAMA (ATSUGI)": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL KAWASAKI'],
        rixoCompany: ['TAA'],
        rixoPrice: ['¥9,900'],
        venueId: ['65010'],
        mappings: [
            {
                rixoCompany: 'TAA',
                rixoPrice: '¥9,900',
                venueId: '65010',
                stockLocation: 'GLOBAL KAWASAKI'
            },
        ]
    },
    "USS FUKUOKA": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL HAKATA'],
        rixoCompany: ["Y'S"],
        rixoPrice: ['¥4,620'],
        venueId: ['E0483'],
        mappings: [
            {
                rixoCompany: "Y'S",
                rixoPrice: '¥4,620',
                venueId: 'E0483',
                stockLocation: 'GLOBAL HAKATA'
            },
        ]
    },
    "USS GUNMA": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL KAWASAKI'],
        rixoCompany: ['LOGICO'],
        rixoPrice: ['¥13,200'],
        venueId: ['E0483'],
        mappings: [
            {
                rixoCompany: 'LOGICO',
                rixoPrice: '¥13,200',
                venueId: 'E0483',
                stockLocation: 'GLOBAL KAWASAKI'
            },
        ]
    },
    "USS HOKURIKU": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL NAGOYA'],
        rixoCompany: ['LOGICO', 'STYLISH AUTO'],
        rixoPrice: ['¥19,800', '¥18,000'],
        venueId: ['E0483'],
        mappings: [
            {
                rixoCompany: 'LOGICO',
                rixoPrice: '¥19,800',
                venueId: 'E0483',
                stockLocation: 'GLOBAL NAGOYA'
            },
            {
                rixoCompany: 'STYLISH AUTO',
                rixoPrice: '¥18,000',
                venueId: 'E0483',
                stockLocation: 'GLOBAL NAGOYA'
            },
        ]
    },
    "USS KOBE": {
        typeOfVehicle: [],
        stockLocation: ['KLC'],
        rixoCompany: ['KLC'],
        rixoPrice: ['¥5,500'],
        venueId: ['E0483'],
        mappings: [
            {
                rixoCompany: 'KLC',
                rixoPrice: '¥5,500',
                venueId: 'E0483',
                stockLocation: 'KLC'
            },
        ]
    },
    "USS KYUSHU": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL HAKATA'],
        rixoCompany: ["Y'S"],
        rixoPrice: ['¥4,620'],
        venueId: ['E0483'],
        mappings: [
            {
                rixoCompany: "Y'S",
                rixoPrice: '¥4,620',
                venueId: 'E0483',
                stockLocation: 'GLOBAL HAKATA'
            },
        ]
    },
    "USS NAGOYA": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL NAGOYA'],
        rixoCompany: ['STYLISH AUTO'],
        rixoPrice: ['¥5,000'],
        venueId: ['E0483'],
        mappings: [
            {
                rixoCompany: 'STYLISH AUTO',
                rixoPrice: '¥5,000',
                venueId: 'E0483',
                stockLocation: 'GLOBAL NAGOYA'
            },
        ]
    },
    "USS NIIGATA": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL KAWASAKI'],
        rixoCompany: ['LOGICO'],
        rixoPrice: ['¥22,200'],
        venueId: ['E0483'],
        mappings: [
            {
                rixoCompany: 'LOGICO',
                rixoPrice: '¥22,200',
                venueId: 'E0483',
                stockLocation: 'GLOBAL KAWASAKI'
            },
        ]
    },
    "USS OKAYAMA": {
        typeOfVehicle: [],
        stockLocation: ['KLC'],
        rixoCompany: ['KLC'],
        rixoPrice: ['¥10,800'],
        venueId: ['E0483'],
        mappings: [
            {
                rixoCompany: 'KLC',
                rixoPrice: '¥10,800',
                venueId: 'E0483',
                stockLocation: 'KLC'
            },
        ]
    },
    "USS OSAKA": {
        typeOfVehicle: [],
        stockLocation: ['KLC'],
        rixoCompany: ['KLC'],
        rixoPrice: ['¥5,500'],
        venueId: ['E0483'],
        mappings: [
            {
                rixoCompany: 'KLC',
                rixoPrice: '¥5,500',
                venueId: 'E0483',
                stockLocation: 'KLC'
            },
        ]
    },
    "USS R-NAGOYA": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL NAGOYA'],
        rixoCompany: ['STYLISH AUTO'],
        rixoPrice: ['¥5,000'],
        venueId: ['E0483'],
        mappings: [
            {
                rixoCompany: 'STYLISH AUTO',
                rixoPrice: '¥5,000',
                venueId: 'E0483',
                stockLocation: 'GLOBAL NAGOYA'
            },
        ]
    },
    "USS SAITAMA": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL KAWASAKI'],
        rixoCompany: ['YAMAZAKI'],
        rixoPrice: ['¥8,000'],
        venueId: ['E0483'],
        mappings: [
            {
                rixoCompany: 'YAMAZAKI',
                rixoPrice: '¥8,000',
                venueId: 'E0483',
                stockLocation: 'GLOBAL KAWASAKI'
            },
        ]
    },
    "USS SAPPORO": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL KAWASAKI'],
        rixoCompany: ['LOGICO'],
        rixoPrice: ['¥34,600'],
        venueId: ['E0483'],
        mappings: [
            {
                rixoCompany: 'LOGICO',
                rixoPrice: '¥34,600',
                venueId: 'E0483',
                stockLocation: 'GLOBAL KAWASAKI'
            },
        ]
    },
    "USS SHIZUOKA": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL NAGOYA'],
        rixoCompany: ['STYLISH AUTO'],
        rixoPrice: ['¥13,000'],
        venueId: ['E0483'],
        mappings: [
            {
                rixoCompany: 'STYLISH AUTO',
                rixoPrice: '¥13,000',
                venueId: 'E0483',
                stockLocation: 'GLOBAL NAGOYA'
            },
        ]
    },
    "USS TOHOKU": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL KAWASAKI'],
        rixoCompany: ['LOGICO'],
        rixoPrice: ['¥19,400'],
        venueId: ['E0483'],
        mappings: [
            {
                rixoCompany: 'LOGICO',
                rixoPrice: '¥19,400',
                venueId: 'E0483',
                stockLocation: 'GLOBAL KAWASAKI'
            },
        ]
    },
    "USS TOKYO": {
        typeOfVehicle: ['CAR', 'G CLASS/ LAND CRUISER/', 'TRUCKS'],
        stockLocation: ['GLOBAL KAWASAKI'],
        rixoCompany: ['YAMAZAKI', 'SHAHBAZ'],
        rixoPrice: ['¥7,000', '¥10,000', '¥15,000'],
        venueId: ['E0483'],
        mappings: [
            {
                typeOfVehicle: 'CAR',
                rixoCompany: 'YAMAZAKI',
                rixoPrice: '¥7,000',
                venueId: 'E0483',
                stockLocation: 'GLOBAL KAWASAKI'
            },
            {
                typeOfVehicle: 'CAR',
                rixoCompany: 'SHAHBAZ',
                rixoPrice: '¥7,000',
                venueId: 'E0483',
                stockLocation: 'GLOBAL KAWASAKI'
            },
            {
                typeOfVehicle: 'G CLASS/ LAND CRUISER/',
                rixoCompany: 'SHAHBAZ',
                rixoPrice: '¥10,000',
                venueId: 'E0483',
                stockLocation: 'GLOBAL KAWASAKI'
            },
            {
                typeOfVehicle: 'TRUCKS',
                rixoCompany: 'SHAHBAZ',
                rixoPrice: '¥15,000',
                venueId: 'E0483',
                stockLocation: 'GLOBAL KAWASAKI'
            },
        ]
    },
    "USS YOKOHAMA": {
        typeOfVehicle: ['TRUCKS BUS'],
        stockLocation: ['GLOBAL KAWASAKI'],
        rixoCompany: ['YAMAZAKI', 'SHAHBAZ'],
        rixoPrice: ['¥4,000', '¥3,500', '¥8,000'],
        venueId: ['E0483'],
        mappings: [
            {
                rixoCompany: 'YAMAZAKI',
                rixoPrice: '¥4,000',
                venueId: 'E0483',
                stockLocation: 'GLOBAL KAWASAKI'
            },
            {
                rixoCompany: 'SHAHBAZ',
                rixoPrice: '¥3,500',
                venueId: 'E0483',
                stockLocation: 'GLOBAL KAWASAKI'
            },
            {
                typeOfVehicle: 'TRUCKS BUS',
                rixoCompany: 'SHAHBAZ',
                rixoPrice: '¥8,000',
                venueId: 'E0483',
                stockLocation: 'GLOBAL KAWASAKI'
            },
        ]
    },
    "ZERO CHIBA": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL KAWASAKI'],
        rixoCompany: ['YAMAZAKI'],
        rixoPrice: ['¥7,000'],
        venueId: ['B2B901'],
        mappings: [
            {
                rixoCompany: 'YAMAZAKI',
                rixoPrice: '¥7,000',
                venueId: 'B2B901',
                stockLocation: 'GLOBAL KAWASAKI'
            },
        ]
    },
    "ZERO HAKATA": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL HAKATA'],
        rixoCompany: ["Y'S"],
        rixoPrice: ['¥7,200'],
        venueId: ['B2B901'],
        mappings: [
            {
                rixoCompany: "Y'S",
                rixoPrice: '¥7,200',
                venueId: 'B2B901',
                stockLocation: 'GLOBAL HAKATA'
            },
        ]
    },
    "ZERO HOKKAIDO": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL KAWASAKI'],
        rixoCompany: ['LOGICO'],
        rixoPrice: ['¥33,600'],
        venueId: ['B2B901'],
        mappings: [
            {
                rixoCompany: 'LOGICO',
                rixoPrice: '¥33,600',
                venueId: 'B2B901',
                stockLocation: 'GLOBAL KAWASAKI'
            },
        ]
    },
    "ZERO OSAKA": {
        typeOfVehicle: [],
        stockLocation: ['KLC'],
        rixoCompany: ['KLC'],
        rixoPrice: [],
        venueId: ['B2B901'],
        mappings: [
            {
                rixoCompany: 'KLC',
                venueId: 'B2B901',
                stockLocation: 'KLC'
            },
        ]
    },
    "ZERO SENDAI": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL KAWASAKI'],
        rixoCompany: ['LOGICO'],
        rixoPrice: ['¥19,800'],
        venueId: ['B2B901'],
        mappings: [
            {
                rixoCompany: 'LOGICO',
                rixoPrice: '¥19,800',
                venueId: 'B2B901',
                stockLocation: 'GLOBAL KAWASAKI'
            },
        ]
    },
    "ZERO SHONAN": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL KAWASAKI'],
        rixoCompany: ['LOGICO'],
        rixoPrice: ['¥8,900'],
        venueId: ['B2B901'],
        mappings: [
            {
                rixoCompany: 'LOGICO',
                rixoPrice: '¥8,900',
                venueId: 'B2B901',
                stockLocation: 'GLOBAL KAWASAKI'
            },
        ]
    },
    "ZIP OSAKA": {
        typeOfVehicle: [],
        stockLocation: ['KLC'],
        rixoCompany: ['KLC'],
        rixoPrice: ['¥5,500'],
        venueId: ['41452'],
        mappings: [
            {
                rixoCompany: 'KLC',
                rixoPrice: '¥5,500',
                venueId: '41452',
                stockLocation: 'KLC'
            },
        ]
    },
    "ZIP TOKYO": {
        typeOfVehicle: [],
        stockLocation: ['GLOBAL KAWASAKI'],
        rixoCompany: ['YAMAZAKI', 'SHAHBAZ'],
        rixoPrice: ['¥8,000', '¥6,000'],
        venueId: ['41452'],
        mappings: [
            {
                rixoCompany: 'YAMAZAKI',
                rixoPrice: '¥8,000',
                venueId: '41452',
                stockLocation: 'GLOBAL KAWASAKI'
            },
            {
                rixoCompany: 'SHAHBAZ',
                rixoPrice: '¥6,000',
                venueId: '41452',
                stockLocation: 'GLOBAL KAWASAKI'
            },
        ]
    },
};


/**
 * Split supplier / master fields that store multiple values in one cell (e.g. "A;B;C").
 * Also normalizes Unicode semicolons (Excel/Sheets) so they split like ASCII ';'.
 */
window.splitMasterListTokens = function(val) {
    if (val == null || val === undefined) return [];
    let s = String(val).trim();
    if (!s) return [];
    s = s.replace(/\uFF1B/g, ';').replace(/\uFE55/g, ';');
    return s.split(';').map(function(x) { return x.trim(); }).filter(function(x) { return x.length > 0; });
};

/**
 * Expand any array entries that are still joined with ';' into separate dropdown options
 * (same semantics as Supplier Map table — ';' separates values, not part of one label).
 */
window.flattenSemicolonValues = function(arr) {
    if (!arr || !Array.isArray(arr)) return [];
    const out = [];
    const seen = new Set();
    for (let i = 0; i < arr.length; i++) {
        const v = arr[i];
        if (v == null || v === undefined) continue;
        const s = String(v).trim();
        if (!s) continue;
        const parts = window.splitMasterListTokens(s);
        for (let j = 0; j < parts.length; j++) {
            const p = parts[j];
            const key = p.toLowerCase();
            if (seen.has(key)) continue;
            seen.add(key);
            out.push(p);
        }
    }
    return out;
};

// Helper function to get unique values from arrays
window.getUniqueValues = function(arr) {
    if (!arr || !Array.isArray(arr)) {
        return [];
    }
    const filtered = arr
        .filter(val => val != null && String(val).trim() !== '')
        .map(val => String(val).trim());
    const flattened = window.flattenSemicolonValues ? window.flattenSemicolonValues(filtered) : filtered;
    return [...new Set(flattened)];
};

// Case-insensitive unique values (first occurrence wins) for master-set dropdowns with "See More"
window.getUniqueValuesCaseInsensitive = function(arr) {
    if (!arr || !Array.isArray(arr)) return [];
    const filtered = arr
        .filter(val => val != null && String(val).trim() !== '')
        .map(val => String(val).trim());
    const flattened = window.flattenSemicolonValues ? window.flattenSemicolonValues(filtered) : filtered;
    const seen = new Set();
    return flattened.filter(val => {
        const lower = val.toLowerCase();
        if (seen.has(lower)) return false;
        seen.add(lower);
        return true;
    });
};

// Helper function to get filtered options based on previous selections
window.getFilteredOptions = function(auctionName, typeOfVehicle, stockLocation, rixoCompany) {
    if (!auctionName || !window.rixoPriceMapping[auctionName]) {
        return {
            typeOfVehicle: [],
            stockLocation: [],
            rixoCompany: [],
            rixoPrice: [],
            venueId: []
        };
    }
    
    const auctionData = window.rixoPriceMapping[auctionName];
    let filteredData = { ...auctionData };
    
    // If rixoCompany is selected, filter other options based on available mappings
    if (rixoCompany && auctionData.mappings) {
        const companyMappings = auctionData.mappings.filter(m => m.rixoCompany === rixoCompany);
        
        if (companyMappings.length > 0) {
            // Filter typeOfVehicle based on company mappings
            const availableTypes = companyMappings
                .map(m => m.typeOfVehicle)
                .filter(t => t && t.trim() !== '');
            
            // Filter rixoPrice based on company mappings
            const availablePrices = companyMappings
                .map(m => m.rixoPrice)
                .filter(p => p && p.trim() !== '');
            
            // Filter venueId based on company mappings
            const availableVenues = companyMappings
                .map(m => m.venueId)
                .filter(v => v && v.trim() !== '');
            
            filteredData.typeOfVehicle = [...new Set(availableTypes)];
            filteredData.rixoPrice = [...new Set(availablePrices)];
            filteredData.venueId = [...new Set(availableVenues)];
        }
    }
    
    return {
        typeOfVehicle: window.getUniqueValues(filteredData.typeOfVehicle),
        stockLocation: window.getUniqueValues(filteredData.stockLocation),
        rixoCompany: window.getUniqueValues(filteredData.rixoCompany),
        rixoPrice: window.getUniqueValues(filteredData.rixoPrice),
        venueId: window.getUniqueValues(filteredData.venueId)
    };
};

// Helper function to find matching mapping based on current selections
window.findMatchingMapping = function(auctionName, typeOfVehicle, rixoCompany) {
    if (!auctionName || !window.rixoPriceMapping[auctionName] || !window.rixoPriceMapping[auctionName].mappings) {
        return null;
    }
    
    const mappings = window.rixoPriceMapping[auctionName].mappings;
    
    // Find exact match
    for (let mapping of mappings) {
        if (typeOfVehicle && rixoCompany) {
            if (mapping.typeOfVehicle === typeOfVehicle && mapping.rixoCompany === rixoCompany) {
                return mapping;
            }
        } else if (typeOfVehicle) {
            if (mapping.typeOfVehicle === typeOfVehicle) {
                return mapping;
            }
        } else if (rixoCompany) {
            if (mapping.rixoCompany === rixoCompany) {
                return mapping;
            }
        }
    }
    
    return null;
};

// Flag to prevent infinite loops during auto-selection
window.isAutoSelecting = false;

// Manual reset function in case flag gets stuck
window.resetAutoSelectionFlag = function() {
    console.log('🔄 Manually resetting auto-selection flag');
    window.isAutoSelecting = false;
};

// Helper function to format rixo price for display (adds ¥ symbol and commas)
window.formatRixoPrice = function(value) {
    if (!value || value === '' || value === null || value === undefined) {
        return '';
    }
    
    // Parse the value - remove any existing formatting
    var numStr = String(value).trim();
    // Remove ¥, Â¥, commas, spaces
    numStr = numStr.replace(/[¥Â¥,\s]/g, '').replace(/Â/g, '');
    
    // Check if it's a valid number
    if (numStr === '' || isNaN(numStr)) {
        return '';
    }
    
    // Convert to number and format with commas
    var num = parseInt(numStr, 10);
    if (isNaN(num)) {
        return '';
    }
    
    // Format with commas: 8000 -> "8,000"
    var formatted = num.toLocaleString('en-US');
    
    // Add ¥ symbol: "8,000" -> "¥8,000"
    return '¥' + formatted;
};

// Helper function to parse rixo price for saving (removes ¥ symbol and commas)
window.parseRixoPrice = function(value) {
    if (!value || value === '' || value === null || value === undefined) {
        return '';
    }
    
    // Convert to string and trim
    var cleaned = String(value).trim();
    
    // Remove all non-numeric characters except decimal point (.)
    // This removes: ¥, Â¥, commas, spaces, letters, and any other symbols
    cleaned = cleaned.replace(/[^\d.]/g, '');
    
    // Remove multiple decimal points (keep only the first one)
    var parts = cleaned.split('.');
    if (parts.length > 2) {
        cleaned = parts[0] + '.' + parts.slice(1).join('');
    }
    
    // Return cleaned numeric string (empty if no numbers found)
    return cleaned;
};

// POL tokens from Supplier Map rows: split "OSAKA;SENBOKU;KOBE" and dedupe (case-insensitive, preserve order).
window.flattenPolTokensFromMappings = function(mappings) {
    var seen = {};
    var out = [];
    (mappings || []).forEach(function(m) {
        var raw = (m && m.pol != null) ? String(m.pol) : '';
        if (!raw || !String(raw).trim()) return;
        raw.split(';').forEach(function(t) {
            var v = String(t).trim();
            if (!v) return;
            var k = v.toLowerCase();
            if (seen[k]) return;
            seen[k] = true;
            out.push(v);
        });
    });
    return out;
};

window.getPolTokensFromRixoMappingForSupplier = function(auctionName) {
    if (!auctionName || !window.rixoPriceMapping) return [];
    var keys = Object.keys(window.rixoPriceMapping);
    var lower = String(auctionName).toLowerCase();
    var match = keys.find(function(k) { return k.toLowerCase() === lower; });
    var key = match || auctionName;
    var data = window.rixoPriceMapping[key];
    if (!data || !Array.isArray(data.mappings)) return [];
    return window.flattenPolTokensFromMappings(data.mappings);
};

function mergePolMappingAndApiLists(mappingPolTokens, apiList) {
    var seen = {};
    var out = [];
    (mappingPolTokens || []).forEach(function(p) {
        var v = String(p).trim();
        if (!v) return;
        var k = v.toUpperCase();
        if (seen[k]) return;
        seen[k] = true;
        out.push(v);
    });
    (apiList || []).forEach(function(p) {
        var v = String(p).trim();
        if (!v) return;
        var k = v.toUpperCase();
        if (seen[k]) return;
        seen[k] = true;
        out.push(v);
    });
    return out;
}

// When stock location changes: pass supplier combobox id ('stockLocation' | 'editStockLocation').
window.fetchPolsAfterStockChange = function(stockFieldId) {
    if (typeof window.isSupplierMapProgrammaticUpdate === 'function' && window.isSupplierMapProgrammaticUpdate()) return;
    if (window.__supplierApplyInFlight === true) return;
    if (!stockFieldId) return;
    var ah = (stockFieldId === 'editStockLocation')
        ? (typeof window.getComboboxValue === 'function' ? window.getComboboxValue('editAuctionName') : '')
        : (typeof window.getComboboxValue === 'function' ? window.getComboboxValue('auctionName') : '');
    var st = typeof window.getComboboxValue === 'function' ? window.getComboboxValue(stockFieldId) : '';
    var mp = (typeof window.getPolTokensFromRixoMappingForSupplier === 'function')
        ? window.getPolTokensFromRixoMappingForSupplier(ah)
        : null;
    if (typeof window.fetchPolsByStockLocationAndUpdate === 'function') {
        window.fetchPolsByStockLocationAndUpdate(ah, st, true, mp);
    }
};

// Fetch POLs for a stock location from rixo_prices and update POL dropdowns; optionally set first value.
// mappingPolTokens: optional POL list from Supplier Map (shown first, then API extras, then master list).
// auctionHouse is required so we don't accidentally pull POLs belonging to a different supplier.
window.fetchPolsByStockLocationAndUpdate = function(auctionHouse, stockLocation, setFirstValue, mappingPolTokens, requestSeq) {
    if (setFirstValue === undefined) setFirstValue = true;
    var seq = (requestSeq != null) ? requestSeq : (window.__supplierMappingSeq || 0);
    var polSelect = document.getElementById('pol');
    var editPolSelect = document.getElementById('editPol');
    if (!stockLocation || String(stockLocation).trim() === '') {
        console.log('[POL] Clearing POL (empty stock location)');
        if (typeof window.updateDropdown === 'function') {
            window.updateDropdown('pol', 'editPol', [], true);
        }
        if (polSelect) { polSelect.value = ''; var inp = document.getElementById('polInput'); if (inp) inp.value = ''; }
        if (editPolSelect && !__rixoSkipAutoEditField('editPol')) { editPolSelect.value = ''; var inp = document.getElementById('editPolInput'); if (inp) inp.value = ''; }
        return Promise.resolve();
    }
    if (!auctionHouse || String(auctionHouse).trim() === '') {
        console.warn('[POL] Missing auctionHouse; cannot fetch POLs from rixo_prices.'); // Keep POL empty
        if (typeof window.updateDropdown === 'function') {
            window.updateDropdown('pol', 'editPol', [], true);
        }
        return Promise.resolve();
    }

    // Fetch from rixo_prices: /api/rixo/prices/by-auction-house/{auctionHouse}
    var url = (typeof window.apiUrl === 'function')
        ? window.apiUrl('rixo/prices/by-auction-house/' + encodeURIComponent(auctionHouse))
        : (typeof apiUrl !== 'undefined' ? apiUrl('rixo/prices/by-auction-house/' + encodeURIComponent(auctionHouse)) : '');

    if (!url) {
        console.warn('[POL] No apiUrl available, skipping POL fetch for:', auctionHouse, stockLocation);
        return Promise.resolve();
    }
    var inflightKey = String(seq) + '|' + String(auctionHouse).trim().toLowerCase() + '|' + String(stockLocation).trim().toLowerCase();
    if (window.__polFetchInflightKey === inflightKey && window.__polFetchInflightPromise) {
        return window.__polFetchInflightPromise;
    }
    console.log('[POL] Fetching POLs from rixo_prices for auctionHouse:', auctionHouse, 'stockLocation:', stockLocation, '->', url);
    var fetchPromise = fetch(url)
        .then(function(r) {
            if (seq !== (window.__supplierMappingSeq || 0)) {
                console.log('[POL] Stale POL response ignored for', auctionHouse, stockLocation);
                return null;
            }
            if (!r.ok) {
                console.warn('[POL] API responded with status:', r.status, r.statusText, 'for', url);
            }
            return r.json();
        })
        .then(function(res) {
            if (res == null || seq !== (window.__supplierMappingSeq || 0)) {
                if (res != null) console.log('[POL] Stale POL apply ignored for', auctionHouse, stockLocation);
                return;
            }
            var prices = (res && res.data && Array.isArray(res.data)) ? res.data : [];
            // Filter by stockLocation then extract distinct non-empty POLs.
            // DB cells often store multiple yards as "A; B"; match if the selected yard equals any token.
            function rowStockMatchesSelected(slRaw, selectedRaw) {
                var sel = selectedRaw != null ? String(selectedRaw).trim() : '';
                if (!sel) return false;
                var sl = slRaw != null ? String(slRaw).trim() : '';
                if (!sl) return false;
                if (sl === sel) return true;
                var parts = sl.split(/[;,]/).map(function(x) { return x.trim(); }).filter(function(x) { return x.length > 0; });
                var u = sel.toUpperCase();
                for (var k = 0; k < parts.length; k++) {
                    if (parts[k].toUpperCase() === u) return true;
                }
                return false;
            }
            var list = [];
            var seen = {};
            prices.forEach(function(p) {
                var sl = (p && p.stockLocation != null) ? String(p.stockLocation).trim() : '';
                if (!rowStockMatchesSelected(sl, stockLocation)) return;
                var pol = (p && p.pol != null) ? String(p.pol).trim() : '';
                if (!pol) return;
                var key = pol.toUpperCase();
                if (!seen[key]) { seen[key] = true; list.push(pol); }
            });

            var mapTok = (mappingPolTokens && mappingPolTokens.length) ? mappingPolTokens : window.getPolTokensFromRixoMappingForSupplier(auctionHouse);
            var merged = mergePolMappingAndApiLists(mapTok, list);

            console.log('[POL] POLs for', auctionHouse, stockLocation, 'api:', list, 'mapping:', mapTok, 'merged:', merged, '(success:', res && res.success, ')');
            if (typeof window.updateDropdown === 'function') {
                window.updateDropdown('pol', 'editPol', merged, true);
            }
            if (setFirstValue && merged.length > 0 && typeof window.setFieldValue === 'function') {
                window.setFieldValue('pol', 'editPol', merged[0]);
            }
        })
        .catch(function(err) {
            if (seq !== (window.__supplierMappingSeq || 0)) {
                console.log('[POL] Stale POL error ignored for', auctionHouse, stockLocation);
                return;
            }
            console.warn('[POL] fetchPolsByStockLocationAndUpdate failed for', auctionHouse, stockLocation, ':', err);
            var fallback = (mappingPolTokens && mappingPolTokens.length)
                ? mappingPolTokens
                : window.getPolTokensFromRixoMappingForSupplier(auctionHouse);
            if (typeof window.updateDropdown === 'function') {
                window.updateDropdown('pol', 'editPol', fallback || [], true);
            }
            if (setFirstValue && fallback && fallback.length > 0 && typeof window.setFieldValue === 'function') {
                window.setFieldValue('pol', 'editPol', fallback[0]);
            }
        });
    window.__polFetchInflightKey = inflightKey;
    window.__polFetchInflightPromise = fetchPromise.finally(function() {
        if (window.__polFetchInflightKey === inflightKey) {
            window.__polFetchInflightKey = '';
            window.__polFetchInflightPromise = null;
        }
    });
    return window.__polFetchInflightPromise;
};

/** True if trimmed value matches some entry in list (case-insensitive). */
function __listContainsTokenCaseInsensitive(list, raw) {
    if (!list || !list.length || raw == null) return false;
    var v = String(raw).trim();
    if (!v) return false;
    var u = v.toUpperCase();
    for (var i = 0; i < list.length; i++) {
        if (String(list[i] == null ? '' : list[i]).trim().toUpperCase() === u) return true;
    }
    return false;
}

/** Prefer current selection when it is still valid for this supplier; otherwise first list entry. */
function __pickPreservedOrFirst(list, currentRaw) {
    if (!list || list.length === 0) return null;
    if (__listContainsTokenCaseInsensitive(list, currentRaw)) {
        var u = String(currentRaw).trim().toUpperCase();
        for (var j = 0; j < list.length; j++) {
            if (String(list[j] == null ? '' : list[j]).trim().toUpperCase() === u) return list[j];
        }
    }
    return list[0];
}

/** Read stock / venue / rixo / POL / vehicle type from the active form (edit purchase vs add). */
function __getCurrentSupplierFieldValues() {
    var g = typeof window.getComboboxValue === 'function'
        ? function(id) { return (window.getComboboxValue(id) || '').trim(); }
        : function(id) {
            var inp = document.getElementById(id + 'Input');
            var sel = document.getElementById(id);
            var v = (inp && inp.value) ? inp.value : ((sel && sel.value) ? sel.value : '');
            return (v || '').trim();
        };
    var vals;
    if (document.getElementById('editAuctionName')) {
        vals = {
            stock: g('editStockLocation'),
            venue: g('editVenueId'),
            rixo: g('editRixoCompany'),
            pol: g('editPol'),
            vehicleType: g('editShipmentSize')
        };
    } else {
        vals = {
            stock: g('stockLocation'),
            venue: g('venueId'),
            rixo: g('rixoCompany'),
            pol: g('pol'),
            vehicleType: g('shipmentSize')
        };
    }
    // After refreshRixoDropdowns → populateDropdownOptions(), clears wipe the DOM; merge snapshot taken before clear
    var snap = window.__rixoSupplierPreserveSnapshot;
    if (snap && typeof snap === 'object') {
        function mer(x, y) {
            var a = (x != null && String(x).trim() !== '') ? String(x).trim() : '';
            var b = (y != null && String(y).trim() !== '') ? String(y).trim() : '';
            return a || b || '';
        }
        return {
            stock: mer(vals.stock, snap.stock),
            venue: mer(vals.venue, snap.venue),
            rixo: mer(vals.rixo, snap.rixo),
            pol: mer(vals.pol, snap.pol),
            vehicleType: mer(vals.vehicleType, snap.vehicleType)
        };
    }
    return vals;
}

function __filterMappingsForSupplierSnapshot(mappings, snap) {
    if (!snap || !mappings || !mappings.length) return mappings || [];
    function polMatches(mPol, selPol) {
        if (!selPol || !String(selPol).trim()) return true;
        var polRaw = mPol != null ? String(mPol).trim() : '';
        if (!polRaw) return true;
        var tokens = polRaw.split(/[;,]/).map(function(x) { return x.trim(); }).filter(Boolean);
        var u = String(selPol).trim().toLowerCase();
        for (var i = 0; i < tokens.length; i++) {
            if (tokens[i].toLowerCase() === u) return true;
        }
        return polRaw.toLowerCase() === u;
    }
    return mappings.filter(function(m) {
        if (snap.stock && m.stockLocation && String(m.stockLocation).trim().toLowerCase() !== String(snap.stock).trim().toLowerCase()) return false;
        if (snap.rixo && m.rixoCompany && String(m.rixoCompany).trim().toLowerCase() !== String(snap.rixo).trim().toLowerCase()) return false;
        if (snap.venue && m.venueId && String(m.venueId).trim().toLowerCase() !== String(snap.venue).trim().toLowerCase()) return false;
        if (snap.pol && !polMatches(m.pol, snap.pol)) return false;
        return true;
    });
}

function __vehicleTypesFromMappings(mappings) {
    return window.getUniqueValuesCaseInsensitive(
        (mappings || []).map(function(m) { return m.typeOfVehicle || m.shipmentSize || ''; })
            .filter(function(t) { return t && String(t).trim() !== '' && String(t).trim() !== '-'; })
    );
}

// Helper function to auto-select related fields
window.autoSelectRelatedFields = function(auctionName, changedField, changedValue) {
    console.log('autoSelectRelatedFields called:', auctionName, changedField, changedValue);

    if (window.__suppressRixoAutoSelect === true) {
        console.log('autoSelectRelatedFields: skipped (__suppressRixoAutoSelect)');
        return;
    }
    if (window.__supplierFlowMode === 'page_load' || window.__editPurchaseHydrating === true ||
        window.__suppressSupplierModalFlow === true) {
        console.log('autoSelectRelatedFields: skipped (page load / hydration)');
        return;
    }
    
    // Prevent infinite loops
    if (window.isAutoSelecting) {
        console.log('Already auto-selecting, skipping to prevent loop');
        // Reset flag if it's been stuck for too long (safety mechanism)
        setTimeout(() => {
            if (window.isAutoSelecting) {
                console.warn('⚠️ Auto-selection flag stuck, resetting...');
                window.isAutoSelecting = false;
            }
        }, 1000);
        return;
    }
    
    // Use try-finally to ensure flag is always reset
    window.isAutoSelecting = true;
    
    try {
        // Normalize auction name and find case-insensitive match
        let normalizedAuctionName = auctionName;
        if (!auctionName || !window.rixoPriceMapping || !window.rixoPriceMapping[auctionName]) {
            // Try case-insensitive lookup
            const mappingKeys = Object.keys(window.rixoPriceMapping || {});
            const match = mappingKeys.find(key => key.toLowerCase() === auctionName.toLowerCase());
            if (match) {
                normalizedAuctionName = match;
                console.log('Found case-insensitive match:', auctionName, '->', normalizedAuctionName);
            }
        }
        
        if (!normalizedAuctionName || !window.rixoPriceMapping || !window.rixoPriceMapping[normalizedAuctionName] || !window.rixoPriceMapping[normalizedAuctionName].mappings) {
            console.log('No mappings found for auction:', auctionName, '(normalized:', normalizedAuctionName, ')');
            console.log('Available auction names:', Object.keys(window.rixoPriceMapping || {}));
        return;
    }
    
        const mappings = window.rixoPriceMapping[normalizedAuctionName].mappings;
    
    if (changedField === 'auctionHouse') {
        const stockLocations = window.getUniqueValuesCaseInsensitive(mappings.map(m => m.stockLocation).filter(s => s && String(s).trim() !== ''));
        const venueIds = window.getUniqueValuesCaseInsensitive(mappings.map(m => m.venueId).filter(v => v && String(v).trim() !== ''));
        const rixoCompanies = window.getUniqueValuesCaseInsensitive(mappings.map(m => m.rixoCompany).filter(c => c && String(c).trim() !== ''));
        const polTokensFromMapping = window.flattenPolTokensFromMappings ? window.flattenPolTokensFromMappings(mappings) : [];

        console.log('Auto-selecting for auction house:', {
            stockLocations: stockLocations,
            venueIds: venueIds,
            rixoCompanies: rixoCompanies,
            polTokensFromMapping: polTokensFromMapping
        });

        if (window.__supplierSkipSilentAutoSelect === true) {
            window.__supplierSkipSilentAutoSelect = false;
            // Supplier map flow applies values via Kotlin; only refresh dropdown options.
            window.rebuildSupplierDependentDropdowns(normalizedAuctionName, {
                autoSelect: false,
                restoreValues: false,
                restoreDelay: 0
            });
            return;
        }

        window.rebuildSupplierDependentDropdowns(normalizedAuctionName, {
            autoSelect: true,
            freshAutoSelect: true,
            restoreDelay: 100
        });
        
    } else if (changedField === 'rixoCompany') {
        // When rixoCompany is selected, filter and update other dropdowns
        const companyMappings = mappings.filter(m => m.rixoCompany === changedValue);
        
        if (companyMappings.length > 0) {
            // Intentionally no-op for Vehicle type + Rixo Price mapping.
        }
    } else if (changedField === 'typeOfVehicle') {
        // When typeOfVehicle is selected, find matching mappings
        const typeMappings = mappings.filter(m => m.typeOfVehicle === changedValue);
        
        if (typeMappings.length > 0) {
            // Get unique values for filtered options
            const availableCompanies = [...new Set(typeMappings.map(m => m.rixoCompany).filter(c => c && c.trim() !== ''))];
            
            console.log('Filtered options for typeOfVehicle', changedValue, ':', {
                companies: availableCompanies,
                prices: []
            });
            
             // Update dropdowns directly for both add and edit forms
            updateDropdown('rixoCompany', 'editRixoCompany', availableCompanies, true);
            
            // Auto-select if only one option for both add and edit forms
            if (availableCompanies.length === 1) {
                setFieldValue('rixoCompany', 'rixoCompany', availableCompanies[0]);
                setFieldValue('rixoCompany', 'editRixoCompany', availableCompanies[0]);
            }
        } else {
            // No mappings found; do not overwrite the user-selected Vehicle type.
        }
    }
    } finally {
        // Always reset the flag, even if there was an error
    window.isAutoSelecting = false;
        console.log('✅ Auto-selection completed, flag reset');
    }
};

// Helper function to update dropdown options with filtered data
window.updateDropdownOptions = function(auctionName, typeOfVehicle, stockLocation, rixoCompany) {
    console.log('updateDropdownOptions called:', auctionName, typeOfVehicle, stockLocation, rixoCompany);
    if (!auctionName) return;
    if (typeof window.rebuildSupplierDependentDropdowns === 'function') {
        window.rebuildSupplierDependentDropdowns(auctionName, { autoSelect: false, restoreValues: true, restoreDelay: 50 });
        return;
    }
    if (!window.rixoPriceMapping[auctionName]) {
        return;
    }
    
    // Get filtered options based on current selections
    const filteredOptions = window.getFilteredOptions(auctionName, typeOfVehicle, stockLocation, rixoCompany);
    
    console.log('Filtered options:', filteredOptions);
    
    // Intentionally do NOT update Vehicle type or Rixo Price dropdowns.
    
    // Update Venue ID dropdown
    updateDropdown('venueId', 'editVenueId', filteredOptions.venueId, true);
    
    // Stock Location and Rixo Company remain unchanged (they don't get filtered)
    const auctionData = window.rixoPriceMapping[auctionName];
    updateDropdown('stockLocation', 'editStockLocation', auctionData.stockLocation, true);
    updateDropdown('rixoCompany', 'editRixoCompany', auctionData.rixoCompany, true);
};

// Master-set field IDs that get case-insensitive dedupe and "See More" as last option
var MASTER_FIELD_IDS = {
    venueId: true, editVenueId: true,
    typeOfVehicle: true, editTypeOfVehicle: true,
    shipmentSize: true, editShipmentSize: true,
    rixoCompany: true, editRixoCompany: true, qpRixoCompany: true,
    stockLocation: true, editStockLocation: true, qpStockLocation: true,
    pol: true, editPol: true
};
var SEE_MORE_VALUE = '__SEE_MORE__';
/** Same value as Kotlin CHASSIS_MASTER_SEP_VALUE — overlay draws a rule; not selectable. */
var SUPPLIER_MASTER_SEP_VALUE = '__CHASSIS_MASTER_SEP__';

function registerSupplierMasterComboId(selectId) {
    window.__supplierMasterComboIds = window.__supplierMasterComboIds || [];
    if (window.__supplierMasterComboIds.indexOf(selectId) < 0) {
        window.__supplierMasterComboIds.push(selectId);
    }
}

function masterApiPathForSupplierField(selectId) {
    var map = {
        'venueId': 'master-menu/venue_id', 'editVenueId': 'master-menu/venue_id',
        'typeOfVehicle': 'master-menu/type_of_vehicle', 'editTypeOfVehicle': 'master-menu/type_of_vehicle',
        'shipmentSize': 'master-menu/type_of_vehicle', 'editShipmentSize': 'master-menu/type_of_vehicle',
        'stockLocation': 'master-menu/stock_location', 'editStockLocation': 'master-menu/stock_location',
        'qpStockLocation': 'master-menu/stock_location',
        'rixoCompany': 'rixo-mapping/distinct-rixo-companies', 'editRixoCompany': 'rixo-mapping/distinct-rixo-companies',
        'qpRixoCompany': 'rixo-mapping/distinct-rixo-companies',
        'pol': 'master-menu/pol', 'editPol': 'master-menu/pol'
    };
    return map[selectId] || null;
}

window.__supplierMasterMenuCache = window.__supplierMasterMenuCache || {};
window.__supplierMasterMenuInflight = window.__supplierMasterMenuInflight || {};

function appendMasterListToSupplierSelect(selectId, list) {
    var sel = document.getElementById(selectId);
    if (!sel) return;
    var forceOpenBuild = isSupplierMasterForceBuild(selectId);
    // Skip master append only when suppress is active AND the select already has real options.
    // Never skip when force-building for ▼ open, or when options were wiped (empty list → dead dropdown).
    if (!forceOpenBuild &&
        window.__suppressRixoAutoSelect === true &&
        window.__supplierApplyInFlight !== true &&
        String(selectId).indexOf('edit') === 0 &&
        supplierSelectRealOptionCount(selectId) > 0) {
        return;
    }
    var seen = {};
    for (var i = 0; i < sel.options.length; i++) {
        var v = (sel.options[i].value || '').trim();
        if (v && v !== SUPPLIER_MASTER_SEP_VALUE && v !== SEE_MORE_VALUE && v !== '__SEE_LESS__') {
            seen[v.toLowerCase()] = true;
        }
    }
    (list || []).forEach(function(item) {
        var it = String(item).trim();
        if (!it) return;
        if (seen[it.toLowerCase()]) return;
        seen[it.toLowerCase()] = true;
        var opt = document.createElement('option');
        opt.value = it;
        opt.textContent = it;
        sel.appendChild(opt);
    });
    if (forceOpenBuild) {
        clearSupplierMasterForceBuild(selectId);
    }
    if (typeof window.syncComboboxInput === 'function') {
        var suppressMaster = typeof window.isSupplierMapProgrammaticUpdate === 'function' && window.isSupplierMapProgrammaticUpdate();
        window.syncComboboxInput(selectId, suppressMaster ? { suppressCascade: true } : undefined);
    }
}

function appendMasterAfterSeparatorForSupplierField(selectId) {
    var path = masterApiPathForSupplierField(selectId);
    if (!path) return;
    var cache = window.__supplierMasterMenuCache;
    if (cache[path]) {
        appendMasterListToSupplierSelect(selectId, cache[path]);
        return;
    }
    var inflight = window.__supplierMasterMenuInflight;
    if (inflight[path]) {
        inflight[path].then(function(list) {
            appendMasterListToSupplierSelect(selectId, list);
        });
        return;
    }
    var url = (typeof window.apiUrl === 'function') ? window.apiUrl(path) : (typeof apiUrl !== 'undefined' ? apiUrl(path) : '');
    if (!url) return;
    inflight[path] = fetch(url)
        .then(function(r) { return r && r.ok ? r.json() : []; })
        .then(function(raw) {
            var list = Array.isArray(raw) ? raw : [];
            cache[path] = list;
            delete inflight[path];
            return list;
        })
        .catch(function(err) {
            delete inflight[path];
            console.warn('[supplier+master] Failed to load master for', selectId, err);
            return [];
        });
    inflight[path].then(function(list) {
        appendMasterListToSupplierSelect(selectId, list);
    });
}

function buildSupplierMappingPlusMasterSelect(selectId, supplierOptions, placeholderLabel) {
    var sel = document.getElementById(selectId);
    if (!sel) return;
    registerSupplierMasterComboId(selectId);
    var seen = {};
    sel.innerHTML = '';
    var def = document.createElement('option');
    def.value = '';
    def.textContent = placeholderLabel || 'Select';
    sel.appendChild(def);
    (supplierOptions || []).forEach(function(opt) {
        var v = String(opt).trim();
        if (!v) return;
        var k = v.toLowerCase();
        if (seen[k]) return;
        seen[k] = true;
        var o = document.createElement('option');
        o.value = v;
        o.textContent = v;
        sel.appendChild(o);
    });
    var sep = document.createElement('option');
    sep.value = SUPPLIER_MASTER_SEP_VALUE;
    sep.textContent = 'Other Options \u2195';
    sep.disabled = true;
    sel.appendChild(sep);
    if (typeof window.syncComboboxInput === 'function') {
        var suppressMaster = typeof window.isSupplierMapProgrammaticUpdate === 'function' && window.isSupplierMapProgrammaticUpdate();
        window.syncComboboxInput(selectId, suppressMaster ? { suppressCascade: true } : undefined);
    }
    appendMasterAfterSeparatorForSupplierField(selectId);
}

function getCurrentAuctionNameForPurchaseForm() {
    var isEdit = !!document.getElementById('editAuctionName');
    var auctionId = isEdit ? 'editAuctionName' : 'auctionName';
    if (typeof window.getComboboxValue === 'function') {
        var v = (window.getComboboxValue(auctionId) || '').trim();
        if (v && v !== '__add_new_supplier__') return v;
    }
    var sel = document.getElementById(auctionId);
    var inp = document.getElementById(auctionId + 'Input');
    var raw = ((inp && inp.value) || (sel && sel.value) || '').trim();
    if (raw && raw !== '__add_new_supplier__') return raw;
    var pd = window.__editPurchaseDataForRixo;
    if (pd && pd.auctionHouse) {
        var fromPd = String(pd.auctionHouse).trim();
        if (fromPd && fromPd !== '__add_new_supplier__') return fromPd;
    }
    var ps = window.__rixoSupplierPreserveSnapshot;
    if (ps && ps.auction) {
        var fromSnap = String(ps.auction).trim();
        if (fromSnap && fromSnap !== '__add_new_supplier__') return fromSnap;
    }
    return '';
}

function normalizeAuctionNameForMapping(auctionName) {
    return resolveRixoMappingKey(auctionName);
}

/** Case-insensitive lookup; falls back to trimmed name for suppliers not yet in cache. */
function resolveRixoMappingKey(auctionName) {
    var name = String(auctionName || '').trim();
    if (!name) return '';
    if (!window.rixoPriceMapping) return name;
    if (window.rixoPriceMapping[name]) return name;
    var keys = Object.keys(window.rixoPriceMapping);
    for (var i = 0; i < keys.length; i++) {
        if (keys[i].toLowerCase() === name.toLowerCase()) return keys[i];
    }
    return name;
}

function supplierSelectHasMasterSep(selectId) {
    var sel = document.getElementById(selectId);
    if (!sel) return false;
    for (var i = 0; i < sel.options.length; i++) {
        if (sel.options[i].value === SUPPLIER_MASTER_SEP_VALUE) return true;
    }
    return false;
}

function supplierSelectRealOptionCount(selectId) {
    var sel = document.getElementById(selectId);
    if (!sel) return 0;
    var n = 0;
    for (var i = 0; i < sel.options.length; i++) {
        var v = (sel.options[i].value || '').trim();
        if (v && v !== SUPPLIER_MASTER_SEP_VALUE && v !== SEE_MORE_VALUE && v !== '__SEE_LESS__') n++;
    }
    return n;
}

/** Timed force-build allowlist so Edit ▼ can refresh options despite suppress/override races. */
window.__supplierMasterForceBuildUntil = window.__supplierMasterForceBuildUntil || {};

function isSupplierMasterForceBuild(selectId) {
    if (!selectId) return false;
    var until = window.__supplierMasterForceBuildUntil && window.__supplierMasterForceBuildUntil[selectId];
    if (!until) return false;
    if (Date.now() > until) {
        delete window.__supplierMasterForceBuildUntil[selectId];
        return false;
    }
    return true;
}

function markSupplierMasterForceBuild(selectId, ms) {
    if (!selectId) return;
    window.__supplierMasterForceBuildUntil = window.__supplierMasterForceBuildUntil || {};
    window.__supplierMasterForceBuildUntil[selectId] = Date.now() + (ms != null ? ms : 5000);
}

function clearSupplierMasterForceBuild(selectId) {
    if (!selectId || !window.__supplierMasterForceBuildUntil) return;
    delete window.__supplierMasterForceBuildUntil[selectId];
}

function mappingTokensForSupplierMasterField(selectId, mappings) {
    mappings = mappings || [];
    var pick = function(getter) {
        return window.getUniqueValuesCaseInsensitive
            ? window.getUniqueValuesCaseInsensitive(mappings.map(getter).filter(function(s) { return s && String(s).trim() !== ''; }))
            : window.getUniqueValues(mappings.map(getter).filter(function(s) { return s && String(s).trim() !== ''; }));
    };
    if (selectId === 'stockLocation' || selectId === 'editStockLocation' || selectId === 'qpStockLocation') {
        return pick(function(m) { return m.stockLocation; });
    }
    if (selectId === 'rixoCompany' || selectId === 'editRixoCompany' || selectId === 'qpRixoCompany') {
        return pick(function(m) { return m.rixoCompany; });
    }
    if (selectId === 'venueId' || selectId === 'editVenueId') {
        return pick(function(m) { return m.venueId; });
    }
    if (selectId === 'pol' || selectId === 'editPol') {
        return window.flattenPolTokensFromMappings
            ? window.flattenPolTokensFromMappings(mappings)
            : pick(function(m) { return m.pol; });
    }
    if (selectId === 'shipmentSize' || selectId === 'editShipmentSize' ||
        selectId === 'typeOfVehicle' || selectId === 'editTypeOfVehicle') {
        return typeof __vehicleTypesFromMappings === 'function'
            ? __vehicleTypesFromMappings(mappings)
            : pick(function(m) { return m.typeOfVehicle || m.vehicleType; });
    }
    return [];
}

function applyPreservedValueToSupplierSelect(selectId, value) {
    if (value == null || String(value).trim() === '') return;
    var val = String(value).trim();
    var sel = document.getElementById(selectId);
    if (!sel) return;
    var matched = false;
    for (var i = 0; i < sel.options.length; i++) {
        var ov = (sel.options[i].value || '').trim();
        var ot = (sel.options[i].text || '').trim();
        if (ov === val || ot === val || ov.toLowerCase() === val.toLowerCase() || ot.toLowerCase() === val.toLowerCase()) {
            sel.value = ov || val;
            matched = true;
            break;
        }
    }
    if (!matched) {
        var o = document.createElement('option');
        o.value = val;
        o.textContent = val;
        sel.appendChild(o);
        sel.value = val;
    }
    var inp = document.getElementById(selectId + 'Input');
    if (inp) inp.value = sel.value || val;
    if (typeof window.syncComboboxInput === 'function') {
        window.syncComboboxInput(selectId, { suppressCascade: true });
    }
}

/** Set value on a supplier master combobox after buildSupplierMappingPlusMasterSelect. */
function restoreSupplierMasterFieldValue(addFieldId, editFieldId, value) {
    if (value == null || String(value).trim() === '') return;
    var val = String(value).trim();
    function applyOne(selectId, v) {
        var sel = document.getElementById(selectId);
        if (!sel) return;
        var matched = false;
        for (var i = 0; i < sel.options.length; i++) {
            var ov = (sel.options[i].value || '').trim();
            var ot = (sel.options[i].text || '').trim();
            if (ov === v || ot === v || ov.toLowerCase() === v.toLowerCase() || ot.toLowerCase() === v.toLowerCase()) {
                sel.value = ov || v;
                matched = true;
                break;
            }
        }
        if (!matched) {
            var o = document.createElement('option');
            o.value = v;
            o.textContent = v;
            sel.appendChild(o);
            sel.value = v;
        }
        var inp = document.getElementById(selectId + 'Input');
        if (inp) inp.value = sel.value || v;
        if (typeof window.syncComboboxInput === 'function') {
            window.syncComboboxInput(selectId, { suppressCascade: true });
        }
    }
    if (document.getElementById(addFieldId)) applyOne(addFieldId, val);
    if (editFieldId !== addFieldId && document.getElementById(editFieldId) && !__rixoSkipAutoEditField(editFieldId)) {
        applyOne(editFieldId, val);
    }
}

/**
 * Rebuild Stock / Venue / Rixo / POL dropdowns from supplier mapping (+ master menu).
 * Call after populateDropdownOptions clears those selects.
 */
window.rebuildSupplierDependentDropdowns = function(auctionName, options) {
    options = options || {};
    var seq = window.__supplierMappingSeq || 0;
    var normalized = resolveRixoMappingKey(auctionName);
    if (!normalized || !window.rixoPriceMapping[normalized] || !window.rixoPriceMapping[normalized].mappings) {
        return false;
    }
    var mappings = window.rixoPriceMapping[normalized].mappings;
    var snapHint = options.preserveSnapshot && typeof options.preserveSnapshot === 'object'
        ? options.preserveSnapshot : null;
    var stockLocations = window.getUniqueValuesCaseInsensitive(mappings.map(function(m) { return m.stockLocation; }).filter(function(s) { return s && String(s).trim() !== ''; }));
    var venueIds = window.getUniqueValuesCaseInsensitive(mappings.map(function(m) { return m.venueId; }).filter(function(v) { return v && String(v).trim() !== ''; }));
    var rixoCompanies = window.getUniqueValuesCaseInsensitive(mappings.map(function(m) { return m.rixoCompany; }).filter(function(c) { return c && String(c).trim() !== ''; }));
    var polTokensFromMapping = window.flattenPolTokensFromMappings ? window.flattenPolTokensFromMappings(mappings) : [];
    // Vehicle type is owned by Chassis Map — do not derive options from rixo mapping here

    window.beginSupplierMapProgrammatic();
    try {
        updateDropdown('stockLocation', 'editStockLocation', stockLocations, true);
        updateDropdown('venueId', 'editVenueId', venueIds, true);
        updateDropdown('rixoCompany', 'editRixoCompany', rixoCompanies, true);
        if (polTokensFromMapping.length > 0) {
            updateDropdown('pol', 'editPol', polTokensFromMapping, true);
        } else {
            updateDropdown('pol', 'editPol', [], true);
        }
        // Vehicle type is owned by Chassis Map — do not rebuild shipmentSize from rixo mapping
    } finally {
        if (!options.holdProgrammaticUntilRestored) {
            window.endSupplierMapProgrammatic();
        }
    }

    var restoreDelay = options.restoreDelay != null ? options.restoreDelay : 100;
    setTimeout(function() {
        if (seq !== (window.__supplierMappingSeq || 0)) {
            console.log('rebuildSupplierDependentDropdowns: stale restore ignored for', normalized);
            if (options.holdProgrammaticUntilRestored && typeof window.forceEndSupplierMapProgrammatic === 'function') {
                window.forceEndSupplierMapProgrammatic();
            }
            return;
        }
        window.beginSupplierMapProgrammatic();
        try {
            var snap = options.preserveSnapshot;
            var cur = options.freshAutoSelect
                ? { stock: '', venue: '', rixo: '', pol: '', vehicleType: '' }
                : (snap && typeof snap === 'object' ? snap : __getCurrentSupplierFieldValues());
            if (options.autoSelect) {
                var stockPick = stockLocations.length > 0 ? __pickPreservedOrFirst(stockLocations, cur.stock) : null;
                var venuePick = venueIds.length > 0 ? __pickPreservedOrFirst(venueIds, cur.venue) : null;
                var rixoPick = rixoCompanies.length > 0 ? __pickPreservedOrFirst(rixoCompanies, cur.rixo) : null;
                var polPick = polTokensFromMapping.length > 0 ? __pickPreservedOrFirst(polTokensFromMapping, cur.pol) : null;
                if (stockPick) restoreSupplierMasterFieldValue('stockLocation', 'editStockLocation', stockPick);
                if (polPick || (polTokensFromMapping.length > 0 && !cur.pol)) {
                    restoreSupplierMasterFieldValue('pol', 'editPol', polPick || polTokensFromMapping[0]);
                } else if (cur.pol) {
                    restoreSupplierMasterFieldValue('pol', 'editPol', cur.pol);
                }
                if (venuePick) restoreSupplierMasterFieldValue('venueId', 'editVenueId', venuePick);
                if (rixoPick) restoreSupplierMasterFieldValue('rixoCompany', 'editRixoCompany', rixoPick);
                // Do not auto-pick / restore Vehicle type from rixo mapping
                if (stockPick && typeof window.fetchPolsByStockLocationAndUpdate === 'function') {
                    var keepPol = !!(cur.pol && __listContainsTokenCaseInsensitive(polTokensFromMapping, cur.pol));
                    window.fetchPolsByStockLocationAndUpdate(normalized, stockPick, !keepPol, polTokensFromMapping, seq);
                }
            } else if (options.restoreValues !== false) {
                if (cur.stock) restoreSupplierMasterFieldValue('stockLocation', 'editStockLocation', cur.stock);
                if (cur.pol) restoreSupplierMasterFieldValue('pol', 'editPol', cur.pol);
                if (cur.venue) restoreSupplierMasterFieldValue('venueId', 'editVenueId', cur.venue);
                if (cur.rixo) restoreSupplierMasterFieldValue('rixoCompany', 'editRixoCompany', cur.rixo);
                // Do not restore Vehicle type from supplier snapshot into form (chassis owns it)
                if (cur.stock && typeof window.fetchPolsByStockLocationAndUpdate === 'function' && !options.skipPolFetchOnRestore) {
                    window.fetchPolsByStockLocationAndUpdate(normalized, cur.stock, false, cur.pol ? [cur.pol] : polTokensFromMapping, seq);
                }
            }
            if (typeof options.onRestored === 'function') {
                options.onRestored();
            }
            if (window.__rixoSupplierPreserveSnapshot && !options.keepPreserveSnapshot &&
                window.__supplierApplyInFlight !== true && window.__supplierFlowMode !== 'page_load') {
                window.__rixoSupplierPreserveSnapshot = null;
            }
        } finally {
            if (!window.__supplierApplyInFlight) {
                window.endSupplierMapProgrammatic();
            }
        }
    }, restoreDelay);
    return true;
};

/**
 * Ensure a supplier master combobox has mapping options (+ separator) before ▼ opens.
 * Does a single-field force rebuild when the select was cleared or skipped by suppress/override
 * races — does not change supplier auto-select behavior.
 */
window.ensureSupplierMasterComboboxReady = function(selectId) {
    if (!MASTER_FIELD_IDS[selectId]) return true;
    var sel = document.getElementById(selectId);
    if (!sel) return true;

    var needsRebuild = !supplierSelectHasMasterSep(selectId) || supplierSelectRealOptionCount(selectId) === 0;
    if (!needsRebuild) return true;

    var preserved = '';
    if (typeof window.getComboboxValue === 'function') {
        preserved = (window.getComboboxValue(selectId) || '').trim();
    }
    if (!preserved) {
        preserved = (sel.value || '').trim();
        var inp = document.getElementById(selectId + 'Input');
        if (!preserved && inp) preserved = (inp.value || '').trim();
    }

    var auction = getCurrentAuctionNameForPurchaseForm();
    var normalized = auction && typeof resolveRixoMappingKey === 'function'
        ? resolveRixoMappingKey(auction)
        : (auction || '');
    var mappings = (normalized && window.rixoPriceMapping[normalized] && window.rixoPriceMapping[normalized].mappings)
        ? window.rixoPriceMapping[normalized].mappings
        : [];
    var supplierOptions = mappingTokensForSupplierMasterField(selectId, mappings);
    var labelBase = String(selectId)
        .replace(/^edit/, '')
        .replace(/^qp/, '')
        .replace(/([A-Z])/g, ' $1')
        .trim();
    var placeholder = 'Select ' + (labelBase || selectId);

    markSupplierMasterForceBuild(selectId, 5000);
    try {
        buildSupplierMappingPlusMasterSelect(selectId, supplierOptions, placeholder);
        if (preserved) applyPreservedValueToSupplierSelect(selectId, preserved);
    } catch (err) {
        clearSupplierMasterForceBuild(selectId);
        console.warn('[supplier+master] ensureSupplierMasterComboboxReady failed for', selectId, err);
        // Fall back to full rebuild if single-field build threw
        if (auction && typeof window.rebuildSupplierDependentDropdowns === 'function') {
            var snap = (typeof window.__snapshotSupplierFormForPreserve === 'function')
                ? window.__snapshotSupplierFormForPreserve()
                : null;
            var opts = { autoSelect: false, restoreDelay: 0 };
            if (snap && (snap.auction || snap.stock || snap.venue || snap.rixo || snap.pol || snap.vehicleType)) {
                opts.preserveSnapshot = snap;
            } else {
                opts.restoreValues = true;
            }
            window.rebuildSupplierDependentDropdowns(auction, opts);
        }
    }
    // If master list came from cache, append already cleared the force flag; otherwise keep until async append.
    if (supplierSelectRealOptionCount(selectId) > 0 && supplierSelectHasMasterSep(selectId)) {
        // Mapping options are enough for ▼ to work even if master append is still in flight.
    }
    return true;
};

function uniqueOptionsForMasterField(options) {
    return window.getUniqueValuesCaseInsensitive ? window.getUniqueValuesCaseInsensitive(options) : window.getUniqueValues(options);
}

// Master fields: mapping values first, horizontal rule, then full master_menu list (same pattern as Venue ID — no See More).
window.updateDropdown = function(elementId, editElementId, options, mergeSupplierThenMaster) {
    console.log('updateDropdown called:', elementId, editElementId, options, 'mergeSupplierThenMaster=', mergeSupplierThenMaster);
    
    if (elementId === 'rixoPrice' || elementId === 'editRixoPrice') {
        // Handle Rixo Price dropdown (special case)
        var dropdown = document.getElementById(elementId + 'Dropdown');
        var editDropdown = document.getElementById(editElementId + 'Dropdown');
        
        if (dropdown) {
            dropdown.innerHTML = '<option value="">▼</option>';
            var uniqueOptions = window.getUniqueValues(options);
            uniqueOptions.forEach(function(option) {
                // Format price for display in dropdown
                var displayValue = window.formatRixoPrice ? window.formatRixoPrice(option) : option;
                // Store raw numeric value
                var rawValue = window.parseRixoPrice ? window.parseRixoPrice(option) : option;
                dropdown.innerHTML += '<option value="' + rawValue + '">' + displayValue + '</option>';
            });
        }
        
        if (editDropdown && !__rixoSkipAutoEditField(editElementId)) {
            editDropdown.innerHTML = '<option value="">▼</option>';
            var uniqueOptions = window.getUniqueValues(options);
            uniqueOptions.forEach(function(option) {
                // Format price for display in dropdown
                var displayValue = window.formatRixoPrice ? window.formatRixoPrice(option) : option;
                // Store raw numeric value
                var rawValue = window.parseRixoPrice ? window.parseRixoPrice(option) : option;
                editDropdown.innerHTML += '<option value="' + rawValue + '">' + displayValue + '</option>';
            });
        }
    } else {
        // Handle regular dropdowns (and their input fields for comboboxes)
        var dropdown = document.getElementById(elementId);
        var editDropdown = document.getElementById(editElementId);
        var inputField = document.getElementById(elementId + 'Input');
        var editInputField = document.getElementById(editElementId + 'Input');
        
        var defaultLabel = elementId.replace(/([A-Z])/g, ' $1').trim();
        var editDefaultLabel = editElementId.replace(/([A-Z])/g, ' $1').trim();
        
        var isMasterField = MASTER_FIELD_IDS[elementId];
        var uniqueOptions = isMasterField ? uniqueOptionsForMasterField(options) : window.getUniqueValues(options);
        
        if (dropdown) {
            if (isMasterField) {
                buildSupplierMappingPlusMasterSelect(elementId, uniqueOptions, 'Select ' + defaultLabel);
            } else {
                dropdown.innerHTML = '<option value="">Select ' + defaultLabel + '</option>';
                uniqueOptions.forEach(function(option) {
                    dropdown.innerHTML += '<option value="' + option + '">' + option + '</option>';
                });
            }
        }
        
        if (editDropdown && !__rixoSkipAutoEditField(editElementId)) {
            var isEditMasterField = MASTER_FIELD_IDS[editElementId];
            var editUniqueOptions = isEditMasterField ? uniqueOptionsForMasterField(options) : window.getUniqueValues(options);
            if (isEditMasterField) {
                buildSupplierMappingPlusMasterSelect(editElementId, editUniqueOptions, 'Select ' + editDefaultLabel);
            } else {
                editDropdown.innerHTML = '<option value="">Select ' + editDefaultLabel + '</option>';
                editUniqueOptions.forEach(function(option) {
                    editDropdown.innerHTML += '<option value="' + option + '">' + option + '</option>';
                });
            }
        }
        
        // Update input placeholders for comboboxes
        if (inputField) {
            inputField.placeholder = 'Select ' + defaultLabel;
        }
        if (editInputField && !__rixoSkipAutoEditField(editElementId)) {
            editInputField.placeholder = 'Select ' + editDefaultLabel;
        }
    }
};

// Helper function to set field values
window.setFieldValue = function(addFieldId, editFieldId, value) {
    console.log('setFieldValue called:', addFieldId, editFieldId, value);
    
    if (!value || value.trim() === '') {
        console.log('⚠️ Empty value provided, skipping');
        return;
    }

    var skipEditWrites = (editFieldId !== addFieldId) && __rixoSkipAutoEditField(editFieldId);
    if (skipEditWrites) {
        console.log('setFieldValue: skipped edit side for', editFieldId, '(suppress or user override)');
    }
    
    const addField = document.getElementById(addFieldId);
    const editField = document.getElementById(editFieldId);
    
    // Also set input fields for comboboxes
    const addInputField = document.getElementById(addFieldId + 'Input');
    const editInputField = document.getElementById(editFieldId + 'Input');
    
    // Set add form values
    if (addField) {
        // For select elements, ensure the option exists before setting value
        if (addField.tagName === 'SELECT') {
            // Check if option exists, if not add it
            const optionExists = Array.from(addField.options).some(opt => opt.value === value);
            if (!optionExists && value) {
                const option = document.createElement('option');
                option.value = value;
                option.textContent = value;
                addField.appendChild(option);
                console.log('➕ Added missing option to add select:', addFieldId, value);
            }
        }
        addField.value = value;
        console.log('✅ Set add select value:', addFieldId, '=', value);
        
        // Sync to input if it's a combobox
        if (addInputField && typeof window.syncComboboxInput === 'function') {
            var suppressCascade = typeof window.isSupplierMapProgrammaticUpdate === 'function' && window.isSupplierMapProgrammaticUpdate();
            window.syncComboboxInput(addFieldId, suppressCascade ? { suppressCascade: true } : undefined);
        }
    }
    if (addInputField) {
        addInputField.value = value;
        console.log('✅ Set add input value:', addFieldId + 'Input', '=', value);
    }
    
    // Set edit form values
    if (!skipEditWrites && editField) {
        // For select elements, ensure the option exists before setting value
        if (editField.tagName === 'SELECT') {
            // Check if option exists, if not add it
            const optionExists = Array.from(editField.options).some(opt => opt.value === value);
            if (!optionExists && value) {
                const option = document.createElement('option');
                option.value = value;
                option.textContent = value;
                editField.appendChild(option);
                console.log('➕ Added missing option to edit select:', editFieldId, value);
            }
        }
        editField.value = value;
        console.log('✅ Set edit select value:', editFieldId, '=', value);
        
        // Sync to input if it's a combobox
        if (editInputField && typeof window.syncComboboxInput === 'function') {
            var suppressEditCascade = typeof window.isSupplierMapProgrammaticUpdate === 'function' && window.isSupplierMapProgrammaticUpdate();
            window.syncComboboxInput(editFieldId, suppressEditCascade ? { suppressCascade: true } : undefined);
        }
    }
    if (!skipEditWrites && editInputField) {
        editInputField.value = value;
        console.log('✅ Set edit input value:', editFieldId + 'Input', '=', value);
    }
    
    // Special handling for rixo price fields (they are input fields, not selects)
    if (addFieldId === 'rixoPrice' || editFieldId === 'editRixoPrice') {
        const addInput = document.getElementById('rixoPriceInput');
        const editInput = document.getElementById('editRixoPriceInput');
        
        // Parse the value to remove all symbols and words, keep only numbers
        var numericValue = window.parseRixoPrice ? window.parseRixoPrice(value) : value;
        
        if (addInput && addFieldId === 'rixoPrice') {
            addInput.value = numericValue;
            console.log('Set rixo price input value (numeric):', numericValue);
            addInput.dispatchEvent(new Event('input', { bubbles: true }));
            addInput.dispatchEvent(new Event('change', { bubbles: true }));
        }
        if (editInput && editFieldId === 'editRixoPrice' && !skipEditWrites) {
            editInput.value = numericValue;
            console.log('Set edit rixo price input value (numeric):', numericValue);
            editInput.dispatchEvent(new Event('input', { bubbles: true }));
            editInput.dispatchEvent(new Event('change', { bubbles: true }));
        }
    }
};

// Test function to verify mapping is working
window.testMapping = function() {
    console.log('=== Testing Mapping System ===');
    console.log('Total auctions:', Object.keys(window.rixoPriceMapping).length);
    
    // Test AUCNETVAA (SAKURA) - should have only Car
    const sakura = window.rixoPriceMapping['AUCNETVAA (SAKURA)'];
    if (sakura) {
        console.log('AUCNETVAA (SAKURA):', sakura.typeOfVehicle, 'should auto-select Car');
    }
    
    // Test CAA TOKYO - should have Car and Truck
    const caaTokyo = window.rixoPriceMapping['CAA TOKYO'];
    if (caaTokyo) {
        console.log('CAA TOKYO:', caaTokyo.typeOfVehicle, 'should show dropdown');
    }
    
    console.log('=== End Test ===');
};

// Manual trigger function for testing
window.triggerAutoSelection = function(auctionName) {
    console.log('=== Manual Auto-Selection Trigger ===');
    console.log('Auction:', auctionName);
    
    if (!auctionName || !window.rixoPriceMapping || !window.rixoPriceMapping[auctionName]) {
        console.log('Invalid auction name or mapping not found');
        return;
    }
    
    const auctionData = window.rixoPriceMapping[auctionName];
    console.log('Auction data:', auctionData);
    
    // Force auto-selection
    if (auctionData.typeOfVehicle.length === 1) {
        const typeSelect = document.getElementById('typeOfVehicle');
        if (typeSelect) {
            typeSelect.value = auctionData.typeOfVehicle[0];
            console.log('✅ Auto-selected Vehicle type:', auctionData.typeOfVehicle[0]);
            typeSelect.dispatchEvent(new Event('change', { bubbles: true }));
        }
    }
    
    if (auctionData.stockLocation.length === 1) {
        const stockSelect = document.getElementById('stockLocation');
        if (stockSelect) {
            stockSelect.value = auctionData.stockLocation[0];
            console.log('✅ Auto-selected Stock Location:', auctionData.stockLocation[0]);
        }
    }
    
    if (auctionData.rixoCompany.length === 1) {
        const companySelect = document.getElementById('rixoCompany');
        if (companySelect) {
            companySelect.value = auctionData.rixoCompany[0];
            console.log('✅ Auto-selected Rixo Company:', auctionData.rixoCompany[0]);
            companySelect.dispatchEvent(new Event('change', { bubbles: true }));
        }
    }
    
    if (auctionData.rixoPrice.length === 1) {
        const priceInput = document.getElementById('rixoPrice');
        if (priceInput) {
            priceInput.value = auctionData.rixoPrice[0];
            console.log('✅ Auto-selected Rixo Price:', auctionData.rixoPrice[0]);
        }
    }
    
    console.log('=== End Manual Trigger ===');
};

// ===== RIXO MAPPING MANAGEMENT FUNCTIONS =====

// Global variables for mapping management
window.currentAuctionName = null;
window.currentCompanyName = null;
window.editingMappingId = null;

// Display mapper for shipment size: map 20ft/40ft to CAR/TRUCK
function displayShipmentSize(rawValue) {
    if (!rawValue) return 'N/A';
    const v = String(rawValue).trim().toLowerCase();
    if (v.includes('20')) return 'CAR';
    if (v.includes('40')) return 'TRUCK';
    return rawValue; // Already a semantic value like CAR/TRUCK
}

// Helper function to escape HTML attributes
function escapeHtmlAttr(str) {
    if (!str) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#x27;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;');
}

// Show manage buttons when dropdowns have values
window.toggleManageButtons = function() {
    // Always show the supplier manage button; click handler already guards selection
    const auctionBtn = document.getElementById('manageAuctionMappings') || document.getElementById('manageEditAuctionMappings');
    if (auctionBtn) {
        auctionBtn.style.display = 'flex';
    }
};

// Show auction mappings modal
window.showAuctionMappingsModal = function(auctionName) {
    console.log('🔧 [DEBUG] showAuctionMappingsModal called with auctionName:', auctionName);
    window.currentAuctionName = auctionName || '';
    window.currentCompanyName = null;
    
    // Check if it's a new supplier (empty auction name)
    const isNewSupplier = !auctionName || auctionName.trim() === '';
    window.isCreatingNewSupplier = isNewSupplier;
    console.log('🔧 [DEBUG] isNewSupplier:', isNewSupplier);
    
    const modal = document.getElementById('rixoMappingModal');
    if (!modal) {
        console.error('❌ [ERROR] Modal element not found!');
        return;
    }
    const title = document.getElementById('modalTitle');
    const body = document.querySelector('.modal-body');
    
    const titleText = isNewSupplier ? 'Manage Mappings for:' : `Manage Mappings for: ${auctionName}`;
    
    title.innerHTML = `
        <div style="display: flex; align-items: center; gap: 12px;">
            <span>${titleText}</span>
            ${isNewSupplier ? `
            <input type="text" id="newSupplierNameInput" placeholder="Enter Supplier Name" required 
                   style="padding: 6px 12px; border: 1px solid #ddd; border-radius: 4px; font-size: 14px; min-width: 200px;">
            ` : ''}
        </div>
    `;
    
    // Show the modal immediately
    modal.style.display = 'block';
    console.log('🔧 [DEBUG] Modal displayed');
    
    body.innerHTML = `
        <div class="mapping-section">
            <div style="display:flex; align-items:center; justify-content: space-between; margin-bottom: 8px;">
                <div style="display:flex; align-items:center; gap: 12px;">
                    <h4 style="margin:0;">Current Mappings</h4>
                    <button type="button" id="addNewRixoMappingBtn" style="padding: 6px 12px; background-color: #3b82f6; color: white; border: none; border-radius: 4px; cursor: pointer; font-weight: 500; font-size: 14px;">Add New Mapping</button>
                </div>
                <div>
                    <label for="companyFilter" style="margin-right:6px; color:#555;">Filter by company</label>
                    <select id="companyFilter" style="padding:6px 8px; border:1px solid #ddd; border-radius:4px;">
                        <option value="">All companies</option>
                    </select>
                </div>
            </div>
            <div id="currentMappings" class="current-mappings">
                <!-- Will be populated dynamically -->
            </div>
        </div>
    `;
    
    // Initialize adding flag
    window.isAddingNewRixoMapping = false;
    
    // Load current mappings (only if supplier name is provided)
    if (!isNewSupplier && auctionName.trim() !== '') {
        loadCurrentMappings(auctionName);
    } else {
        // For new supplier, show empty state
        const container = document.getElementById('currentMappings');
        container.innerHTML = '<p style="color: #666; text-align: center; padding: 20px;">Enter supplier name and add mappings</p>';
        window._lastLoadedRixoMappings = [];
    }
    
    // Ensure modal is displayed
    modal.style.display = 'block';
    
    // Add event listeners
    document.getElementById('addNewRixoMappingBtn').addEventListener('click', () => addNewRixoMappingRow(auctionName || ''));
    
    // Supplier name input field change handler (update title dynamically)
    if (isNewSupplier) {
        const supplierNameInput = document.getElementById('newSupplierNameInput');
        if (supplierNameInput) {
            supplierNameInput.addEventListener('input', function() {
                const newSupplierName = this.value.trim();
                if (newSupplierName) {
                    // Update global supplier name
                    window.currentAuctionName = newSupplierName;
                    // Update title
                    const titleSpan = title.querySelector('span');
                    if (titleSpan) {
                        titleSpan.textContent = `Manage Mappings for: ${newSupplierName}`;
                    }
                } else {
                    const titleSpan = title.querySelector('span');
                    if (titleSpan) {
                        titleSpan.textContent = 'Manage Mappings for:';
                    }
                }
            });
        }
    }
    
    // Setup Save Changes button handler
    const saveBtn = document.getElementById('saveMappings');
    if (saveBtn) {
        // Remove existing listeners by cloning
        const newSaveBtn = saveBtn.cloneNode(true);
        saveBtn.parentNode.replaceChild(newSaveBtn, saveBtn);
        newSaveBtn.addEventListener('click', saveRixoMappingsChanges);
    }
    
    // Setup Cancel button handler
    const cancelBtn = document.getElementById('cancelMappings');
    if (cancelBtn) {
        // Remove existing listeners by cloning
        const newCancelBtn = cancelBtn.cloneNode(true);
        cancelBtn.parentNode.replaceChild(newCancelBtn, cancelBtn);
        newCancelBtn.addEventListener('click', closeMappingModal);
    }
    
    // Add event listeners for modal buttons
    setupModalEventListeners();
};

// Show company mappings modal
// Removed company-specific modal; supplier modal now supports filtering by company

// Load current mappings from backend, fallback to static data if empty
window.loadCurrentMappings = function(auctionName, companyName = null) {
    // Check if supplier name is empty (new supplier)
    if (!auctionName || auctionName.trim() === '') {
        const container = document.getElementById('currentMappings');
        if (container) {
            container.innerHTML = '<p style="color: #666; text-align: center; padding: 20px;">Enter supplier name and add mappings</p>';
        }
        window._lastLoadedRixoMappings = [];
        return;
    }
    
    const url = apiUrl(`rixo/mappings/by-auction/${encodeURIComponent(auctionName)}`);
    
    // Helper function to load from static data
    const loadFromStaticData = (isFallback = false) => {
        if (window.rixoPriceMapping && window.rixoPriceMapping[auctionName]) {
            console.log('Loading mappings from static data for:', auctionName);
            const auctionData = window.rixoPriceMapping[auctionName];
            const staticMappings = auctionData.mappings || [];
            
            // Convert static data format to mapping format
            const staticDataAsMappings = [];
            const seen = new Set(); // Track unique combinations
            
            staticMappings.forEach(mapping => {
                const key = `${mapping.stockLocation}_${mapping.rixoCompany}_${mapping.shipmentSize || ''}_${mapping.venueId || ''}`;
                if (!seen.has(key)) {
                    seen.add(key);
                    staticDataAsMappings.push({
                        id: null, // Static data doesn't have DB IDs
                        auctionHouse: auctionName,
                        stockLocation: mapping.stockLocation,
                        rixoCompany: mapping.rixoCompany,
                        shipmentSize: mapping.shipmentSize,
                        rixoPrice: mapping.rixoPrice,
                        venueId: mapping.venueId,
                        _isFallback: isFallback // Mark as fallback data
                    });
                }
            });
            
            // Filter by company if specified
            let mappings = staticDataAsMappings;
            if (companyName) {
                mappings = staticDataAsMappings.filter(m => m.rixoCompany === companyName);
            }
            
            console.log('Loaded from static data:', mappings.length, 'mappings');
            // Sort mappings by ID descending (newest first) - for static data, sort by order in array (reversed)
            const sortedMappings = mappings.slice().reverse(); // Reverse to show newest first
            
            // Store and render with filter
            window._lastLoadedMappings = sortedMappings;
            window._isFallbackMode = isFallback; // Track if we're in fallback mode
            populateCompanyFilter(sortedMappings);
            renderMappingsWithFilter();
            return true;
        }
        return false;
    };
    
    fetch(url)
        .then(response => {
            if (!response.ok) {
                console.warn('API returned error status:', response.status, '- falling back to static data');
                // Try to load from static data on API error (mark as fallback)
                if (loadFromStaticData(true)) {
                    return null; // Successfully loaded from static data
                }
                throw new Error(`HTTP ${response.status}: ${response.statusText}`);
            }
            return response.json();
        })
        .then(data => {
            if (!data) {
                // Already handled by static data fallback
                return;
            }
            
            if (data.success) {
                let mappings = data.data;
                
                // Filter by company if specified
                if (companyName) {
                    mappings = mappings.filter(m => m.rixoCompany === companyName);
                }
                
                // If database returns empty, check static data as fallback
                if (mappings.length === 0) {
                    if (loadFromStaticData(false)) {
                        return; // Successfully loaded from static data
                    }
                }
                
                // Sort mappings by ID descending (newest first)
                const sortedMappings = mappings.sort((a, b) => {
                    const idA = a.id || 0;
                    const idB = b.id || 0;
                    return idB - idA; // Descending order
                });
                
                // Store and render with filter
                window._lastLoadedMappings = sortedMappings;
                populateCompanyFilter(sortedMappings);
                renderMappingsWithFilter();
            } else {
                console.warn('API returned success=false, falling back to static data');
                // Try to load from static data
                if (!loadFromStaticData(true)) {
                    console.error('Failed to load mappings:', data.message);
                    showMessage('Failed to load mappings: ' + data.message, 'error');
                }
            }
        })
        .catch(error => {
            console.error('Error loading mappings from API, trying static data:', error);
            // Try to load from static data on any error (mark as fallback)
            if (!loadFromStaticData(true)) {
                showMessage('Error loading mappings. Please check your connection.', 'error');
            }
        });
};

// Populate company filter dropdown
function populateCompanyFilter(mappings) {
    const filter = document.getElementById('companyFilter');
    if (!filter) return;
    const companies = Array.from(new Set(mappings.map(m => m.rixoCompany).filter(Boolean))).sort();
    filter.innerHTML = '<option value="">All companies</option>' + companies.map(c => `<option value="${escapeHtmlAttr(c)}">${c}</option>`).join('');
    filter.onchange = renderMappingsWithFilter;
}

// Render mappings based on selected filter
function renderMappingsWithFilter() {
    const filter = document.getElementById('companyFilter');
    const selected = filter ? filter.value : '';
    const data = Array.isArray(window._lastLoadedMappings) ? window._lastLoadedMappings : [];
    const filtered = selected ? data.filter(m => m.rixoCompany === selected) : data;
    displayCurrentMappings(filtered);
}

// Display current mappings in the modal
window.displayCurrentMappings = function(mappings) {
    const container = document.getElementById('currentMappings');
    
    // Check if we're adding a new mapping - if so, render table even if empty
    const isAddingNew = window.isAddingNewRixoMapping === true;
    
    if (mappings.length === 0 && !isAddingNew) {
        container.innerHTML = '<div style="padding: 20px; text-align: center; color: #6c757d;">No mappings found</div>';
        return;
    }
    
    // Build table header + rows
    const headerHtml = `
        <div class="mapping-item mapping-header">
            <div class="mapping-field"><strong>Vehicle type</strong></div>
            <div class="mapping-field"><strong>Stock Location</strong></div>
            <div class="mapping-field"><strong>Rixo Company</strong></div>
            <div class="mapping-field"><strong>Price</strong></div>
            <div class="mapping-field"><strong>Venue ID</strong></div>
            <div class="mapping-actions"><strong>Actions</strong></div>
        </div>`;
    // Show warning if in fallback mode
    const isFallbackMode = window._isFallbackMode === true;
    let warningHtml = '';
    if (isFallbackMode) {
        warningHtml = `<div style="background: #fff3cd; border: 1px solid #ffc107; padding: 10px; margin-bottom: 15px; border-radius: 4px; color: #856404;">
            <strong>⚠️ Backend API Unavailable:</strong> Showing mappings from static data. These mappings may already exist in the database. 
            Editing is disabled until the backend API is available.
        </div>`;
    }
    
    // Check if we're adding a new mapping - insert editable row first (isAddingNew already declared above)
    let newRowHtml = '';
    if (isAddingNew) {
        newRowHtml = `
        <div class="mapping-item" id="newRixoMappingRow" style="background-color: #fff9e6;">
            <div class="mapping-field">
                <input type="text" id="inlineNewVehicleType" placeholder="Vehicle Type" style="width: 100%; padding: 4px; border: 1px solid #ccc; border-radius: 4px; box-sizing: border-box;">
            </div>
            <div class="mapping-field">
                <input type="text" id="inlineNewStockLocation" placeholder="Stock Location" style="width: 100%; padding: 4px; border: 1px solid #ccc; border-radius: 4px; box-sizing: border-box;">
            </div>
            <div class="mapping-field">
                <input type="text" id="inlineNewRixoCompany" placeholder="Rixo Company" style="width: 100%; padding: 4px; border: 1px solid #ccc; border-radius: 4px; box-sizing: border-box;">
            </div>
            <div class="mapping-field">
                <input type="text" id="inlineNewRixoPrice" placeholder="Price" style="width: 100%; padding: 4px; border: 1px solid #ccc; border-radius: 4px; box-sizing: border-box;">
            </div>
            <div class="mapping-field">
                <input type="text" id="inlineNewVenueId" placeholder="Venue ID" style="width: 100%; padding: 4px; border: 1px solid #ccc; border-radius: 4px; box-sizing: border-box;">
            </div>
            <div class="mapping-actions">
                <button type="button" id="saveNewRixoMappingBtn" style="padding: 4px 8px; margin-right: 6px; background-color: #28a745; color: white; border: none; border-radius: 4px; cursor: pointer; white-space: nowrap;">Save</button>
                <button type="button" id="cancelNewRixoMappingBtn" style="padding: 4px 8px; background-color: #dc3545; color: white; border: none; border-radius: 4px; cursor: pointer; white-space: nowrap;">Cancel</button>
            </div>
        </div>`;
    }
    
    const rowsHtml = mappings.map(mapping => {
        const isStaticData = mapping.id === null || mapping.id === undefined;
        let actionButtons;
        if (isStaticData) {
            // In fallback mode, don't show "Add to DB" button to prevent duplicates
            if (isFallbackMode) {
                actionButtons = `<span style="color: #6c757d; font-style: italic;">Backend unavailable</span>`;
            } else {
                // Store data in data attributes to avoid escaping issues
                const dataAttrs = `data-auction="${escapeHtmlAttr(mapping.auctionHouse)}" data-stock="${escapeHtmlAttr(mapping.stockLocation)}" data-company="${escapeHtmlAttr(mapping.rixoCompany)}" data-size="${escapeHtmlAttr(mapping.shipmentSize || '')}" data-price="${escapeHtmlAttr(mapping.rixoPrice || '')}" data-venue="${escapeHtmlAttr(mapping.venueId || '')}"`;
                actionButtons = `<button class="edit-mapping-btn add-static-btn" ${dataAttrs} title="Add to Database">Add to DB</button>`;
            }
        } else {
            actionButtons = `<button class="edit-mapping-btn" onclick="editMapping(${mapping.id})">Edit</button>
               <button class="delete-mapping-btn" onclick="deleteMapping(${mapping.id})">Delete</button>`;
        }
        
        return `
        <div class="mapping-item" data-id="${mapping.id || 'static'}">
            <div class="mapping-field">${displayShipmentSize(mapping.shipmentSize)}</div>
            <div class="mapping-field">${mapping.stockLocation}</div>
            <div class="mapping-field">${mapping.rixoCompany}</div>
            <div class="mapping-field">${mapping.rixoPrice || 'N/A'}</div>
            <div class="mapping-field">${mapping.venueId || 'N/A'}</div>
            <div class="mapping-actions">
                ${actionButtons}
            </div>
        </div>`;
    }).join('');
    container.innerHTML = warningHtml + headerHtml + newRowHtml + rowsHtml;
    
    // Setup event listeners for Save/Cancel buttons if new row exists
    if (isAddingNew) {
        const saveBtn = document.getElementById('saveNewRixoMappingBtn');
        const cancelBtn = document.getElementById('cancelNewRixoMappingBtn');
        
        if (saveBtn) {
            // Remove existing listeners by cloning
            const newSaveBtn = saveBtn.cloneNode(true);
            saveBtn.parentNode.replaceChild(newSaveBtn, saveBtn);
            // Prevent duplicate submissions
            let isSaving = false;
            newSaveBtn.addEventListener('click', function() {
                if (isSaving) {
                    console.log('Already saving, ignoring duplicate click');
                    return;
                }
                isSaving = true;
                newSaveBtn.disabled = true;
                saveNewRixoMappingRow(window.currentAuctionName);
                // Reset flag after a delay
                setTimeout(() => {
                    isSaving = false;
                    if (newSaveBtn) {
                        newSaveBtn.disabled = false;
                    }
                }, 2000);
            });
        }
        
        if (cancelBtn) {
            // Remove existing listeners by cloning
            const newCancelBtn = cancelBtn.cloneNode(true);
            cancelBtn.parentNode.replaceChild(newCancelBtn, cancelBtn);
            newCancelBtn.addEventListener('click', () => cancelNewRixoMappingRow(window.currentAuctionName));
        }
    }
    
    // Add event listeners for static mapping buttons
    container.querySelectorAll('.add-static-btn').forEach(btn => {
        btn.addEventListener('click', function() {
            addStaticMappingToDatabase(
                this.dataset.auction,
                this.dataset.stock,
                this.dataset.company,
                this.dataset.size,
                this.dataset.price,
                this.dataset.venue
            );
        });
    });
};

// Add new mapping
window.addNewMapping = function(auctionName) {
    const vehicleType = document.getElementById('newVehicleType').value;
    const stockLocation = document.getElementById('newStockLocation').value;
    const rixoCompany = document.getElementById('newRixoCompany').value;
    const rixoPrice = document.getElementById('newRixoPrice').value;
    const venueId = document.getElementById('newVenueId').value;
    
    // Validate: at least one field must be filled
    if (!vehicleType && !stockLocation && !rixoCompany && !rixoPrice && !venueId) {
        showMessage('Please fill in at least one field', 'error');
        return;
    }
    
    const mappingData = {
        auctionHouse: auctionName,
        vehicleType: vehicleType,
        stockLocation: stockLocation,
        rixoCompany: rixoCompany,
        rixoPrice: rixoPrice,
        venueId: venueId
    };
    
    fetch(apiUrl('rixo/mappings/add'), {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(mappingData)
    })
    .then(response => response.json())
    .then(data => {
        if (data.success) {
            showMessage('Mapping added successfully', 'success');
            clearMappingForm();
            loadCurrentMappings(auctionName, window.currentCompanyName);
            refreshRixoDropdowns();
        } else {
            showMessage('Failed to add mapping: ' + data.message, 'error');
        }
    })
    .catch(error => {
        console.error('Error adding mapping:', error);
        showMessage('Error adding mapping', 'error');
    });
};

// Add new mapping row (inline in table)
window.addNewRixoMappingRow = function(auctionName) {
    if (window.isAddingNewRixoMapping === true) {
        showMessage('Please save or cancel the current new mapping first', 'warning');
        return;
    }
    
    // Check if it's a new supplier creation
    const isNewSupplier = window.isCreatingNewSupplier === true;
    let supplierName = auctionName || '';
    
    if (isNewSupplier) {
        const supplierNameInput = document.getElementById('newSupplierNameInput');
        supplierName = supplierNameInput ? supplierNameInput.value.trim() : '';
    }
    
    if (!supplierName) {
        showMessage('Supplier name is required. Please enter a supplier name in the input field.', 'error');
        return;
    }
    
    window.isAddingNewRixoMapping = true;
    
    // Reload mappings to show the new row
    loadCurrentMappings(supplierName, window.currentCompanyName);
    
    // Disable Add New Mapping button
    const addBtn = document.getElementById('addNewRixoMappingBtn');
    if (addBtn) {
        addBtn.disabled = true;
        addBtn.style.opacity = '0.5';
        addBtn.style.cursor = 'not-allowed';
    }
};

// Save new rixo mapping row
window.saveNewRixoMappingRow = function(auctionName) {
    // Prevent duplicate submissions
    if (window._isSavingRixoMapping === true) {
        console.log('Already saving mapping, ignoring duplicate call');
        return;
    }
    
    // Get supplier name from input field if it's a new supplier, otherwise from parameter
    const isNewSupplier = window.isCreatingNewSupplier === true;
    let supplierName = auctionName || '';
    
    if (isNewSupplier) {
        const supplierNameInput = document.getElementById('newSupplierNameInput');
        supplierName = supplierNameInput ? supplierNameInput.value.trim() : '';
    }
    
    if (!supplierName) {
        showMessage('Supplier name is required. Please enter a supplier name in the input field.', 'error');
        return;
    }
    
    const vehicleType = document.getElementById('inlineNewVehicleType')?.value?.trim() || '';
    const stockLocation = document.getElementById('inlineNewStockLocation')?.value?.trim() || '';
    const rixoCompany = document.getElementById('inlineNewRixoCompany')?.value?.trim() || '';
    const rixoPrice = document.getElementById('inlineNewRixoPrice')?.value?.trim() || '';
    const venueId = document.getElementById('inlineNewVenueId')?.value?.trim() || '';
    
    // Validate: at least one field must be filled
    if (!vehicleType && !stockLocation && !rixoCompany && !rixoPrice && !venueId) {
        showMessage('Please fill in at least one field', 'error');
        return;
    }
    
    // Set saving flag
    window._isSavingRixoMapping = true;
    
    // Disable save button to prevent duplicate clicks
    const saveBtn = document.getElementById('saveNewRixoMappingBtn');
    if (saveBtn) {
        saveBtn.disabled = true;
        saveBtn.textContent = 'Saving...';
    }
    
    const mappingData = {
        auctionHouse: supplierName,
        vehicleType: vehicleType,
        stockLocation: stockLocation,
        rixoCompany: rixoCompany,
        rixoPrice: rixoPrice || null,
        venueId: venueId || null
    };
    
    fetch(apiUrl('rixo/mappings/add'), {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(mappingData)
    })
    .then(response => response.json())
    .then(data => {
        // Reset saving flag
        window._isSavingRixoMapping = false;
        
        if (data.success) {
            showMessage('Mapping added successfully', 'success');
            // Update global supplier name if it was a new supplier
            if (isNewSupplier) {
                window.currentAuctionName = supplierName;
                window.isCreatingNewSupplier = false;
                // Update title
                const title = document.getElementById('modalTitle');
                const titleSpan = title ? title.querySelector('span') : null;
                if (titleSpan) {
                    titleSpan.textContent = `Manage Mappings for: ${supplierName}`;
                }
                // Add new supplier to dropdown lists
                if (typeof refreshSupplierDropdown === 'function') {
                    refreshSupplierDropdown();
                }
            }
            // Reset adding flag
            window.isAddingNewRixoMapping = false;
            // Enable Add New Mapping button
            const addBtn = document.getElementById('addNewRixoMappingBtn');
            if (addBtn) {
                addBtn.disabled = false;
                addBtn.style.opacity = '1';
                addBtn.style.cursor = 'pointer';
            }
            // Reload mappings
            loadCurrentMappings(supplierName, window.currentCompanyName);
            if (typeof refreshRixoDropdowns === 'function') {
                refreshRixoDropdowns();
            }
        } else {
            showMessage('Failed to add mapping: ' + data.message, 'error');
            // Re-enable save button on error
            if (saveBtn) {
                saveBtn.disabled = false;
                saveBtn.textContent = 'Save';
            }
        }
    })
    .catch(error => {
        console.error('Error adding mapping:', error);
        showMessage('Error adding mapping', 'error');
        // Reset saving flag on error
        window._isSavingRixoMapping = false;
        // Re-enable save button on error
        if (saveBtn) {
            saveBtn.disabled = false;
            saveBtn.textContent = 'Save';
        }
    });
};

// Cancel new rixo mapping row
window.cancelNewRixoMappingRow = function(auctionName) {
    // Reset adding flag
    window.isAddingNewRixoMapping = false;
    
    // Enable Add New Mapping button
    const addBtn = document.getElementById('addNewRixoMappingBtn');
    if (addBtn) {
        addBtn.disabled = false;
        addBtn.style.opacity = '1';
        addBtn.style.cursor = 'pointer';
    }
    
    // Reload mappings
    loadCurrentMappings(auctionName, window.currentCompanyName);
};

// Add static mapping to database
window.addStaticMappingToDatabase = function(auctionHouse, stockLocation, rixoCompany, shipmentSize, rixoPrice, venueId) {
    const mappingData = {
        auctionHouse: auctionHouse,
        stockLocation: stockLocation,
        rixoCompany: rixoCompany,
        shipmentSize: shipmentSize || null,
        rixoPrice: rixoPrice || null,
        venueId: venueId || null
    };
    
    fetch(apiUrl('rixo/mappings/add'), {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(mappingData)
    })
    .then(response => response.json())
    .then(data => {
        if (data.success) {
            showMessage('Mapping added to database successfully', 'success');
            // Reload mappings to show the new database entry
            loadCurrentMappings(auctionHouse, window.currentCompanyName);
            refreshRixoDropdowns();
        } else {
            showMessage('Failed to add mapping: ' + data.message, 'error');
        }
    })
    .catch(error => {
        console.error('Error adding static mapping to database:', error);
        showMessage('Error adding mapping to database', 'error');
    });
};

// Edit mapping
window.editMapping = function(mappingId) {
    window.editingMappingId = mappingId;
    
    // Find the mapping item
    const mappingItem = document.querySelector(`[data-id="${mappingId}"]`);
    if (!mappingItem) {
        console.error('Mapping item not found for ID:', mappingId);
        return;
    }
    
    // Get the actual mapping data from stored mappings
    const mappings = Array.isArray(window._lastLoadedMappings) ? window._lastLoadedMappings : [];
    const mapping = mappings.find(m => m.id == mappingId);
    
    if (!mapping) {
        console.error('Mapping data not found for ID:', mappingId);
        console.error('Available mappings:', mappings.map(m => ({ id: m.id, auctionHouse: m.auctionHouse })));
        showMessage('Error: Mapping data not found', 'error');
        return;
    }
    
    console.log('Found mapping for edit:', mapping);
    
    // Get values from mapping data, handling null/undefined/N/A
    // Use shipmentSize (from API) or typeOfVehicle (from static data)
    const vehicleType = (mapping.shipmentSize || mapping.typeOfVehicle) ? 
        (mapping.shipmentSize || mapping.typeOfVehicle) : '';
    const stockLocation = (mapping.stockLocation || '').trim();
    const rixoCompany = (mapping.rixoCompany || '').trim();
    const rixoPrice = (mapping.rixoPrice && mapping.rixoPrice !== 'N/A') ? mapping.rixoPrice : '';
    const venueId = (mapping.venueId && mapping.venueId !== 'N/A') ? mapping.venueId : '';
    
    // Validate that required fields are present
    if (!rixoCompany) {
        console.error('Rixo Company is empty in mapping data:', mapping);
        showMessage('Error: Rixo Company is missing in mapping data', 'error');
        return;
    }
    
    // Use unique IDs per row to avoid conflicts
    const uniqueId = `edit_${mappingId}_`;
    
    // Replace with input fields
    mappingItem.innerHTML = `
        <div class="mapping-field">
            <input type="text" value="${escapeHtmlAttr(vehicleType)}" class="mapping-input" id="${uniqueId}VehicleType" placeholder="Vehicle Type">
        </div>
        <div class="mapping-field">
            <input type="text" value="${escapeHtmlAttr(stockLocation)}" class="mapping-input" id="${uniqueId}StockLocation" placeholder="Stock Location">
        </div>
        <div class="mapping-field">
            <input type="text" value="${escapeHtmlAttr(rixoCompany)}" class="mapping-input" id="${uniqueId}RixoCompany" placeholder="Rixo Company" required>
        </div>
        <div class="mapping-field">
            <input type="text" value="${escapeHtmlAttr(rixoPrice)}" class="mapping-input" id="${uniqueId}RixoPrice" placeholder="Price">
        </div>
        <div class="mapping-field">
            <input type="text" value="${escapeHtmlAttr(venueId)}" class="mapping-input" id="${uniqueId}VenueId" placeholder="Venue ID">
        </div>
        <div class="mapping-actions">
            <button class="edit-mapping-btn" onclick="saveMapping(${mappingId})">Save</button>
            <button class="delete-mapping-btn" onclick="cancelEdit(${mappingId})">Cancel</button>
        </div>
    `;
    
    console.log('Edit mapping initialized:', { mappingId, vehicleType, stockLocation, rixoCompany, rixoPrice, venueId });
};

// Save mapping changes
window.saveMapping = function(mappingId) {
    // Use unique IDs per row
    const uniqueId = `edit_${mappingId}_`;
    
    const vehicleTypeEl = document.getElementById(`${uniqueId}VehicleType`);
    const stockLocationEl = document.getElementById(`${uniqueId}StockLocation`);
    const rixoCompanyEl = document.getElementById(`${uniqueId}RixoCompany`);
    const rixoPriceEl = document.getElementById(`${uniqueId}RixoPrice`);
    const venueIdEl = document.getElementById(`${uniqueId}VenueId`);
    
    if (!vehicleTypeEl || !stockLocationEl || !rixoCompanyEl || !rixoPriceEl || !venueIdEl) {
        console.error('Edit fields not found for mapping ID:', mappingId);
        showMessage('Error: Edit fields not found. Please refresh and try again.', 'error');
        return;
    }
    
    const vehicleType = vehicleTypeEl.value.trim();
    const stockLocation = stockLocationEl.value.trim();
    const rixoCompany = rixoCompanyEl.value.trim();
    const rixoPrice = rixoPriceEl.value.trim();
    const venueId = venueIdEl.value.trim();
    
    // Validate: at least one field must be filled
    if (!vehicleType && !stockLocation && !rixoCompany && !rixoPrice && !venueId) {
        showMessage('Please fill in at least one field', 'error');
        return;
    }
    
    const mappingData = {
        vehicleType: vehicleType || null,
        stockLocation: stockLocation,
        rixoCompany: rixoCompany,
        rixoPrice: rixoPrice || null,
        venueId: venueId || null
    };
    
    console.log('Saving mapping:', mappingId, mappingData);
    
    fetch(apiUrl(`rixo/mappings/${mappingId}`), {
        method: 'PUT',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(mappingData)
    })
    .then(response => {
        if (!response.ok) {
            return response.json().then(err => {
                throw new Error(err.message || `HTTP ${response.status}`);
            });
        }
        return response.json();
    })
    .then(data => {
        if (data.success) {
            showMessage('Mapping updated successfully', 'success');
            // Reload mappings to show updated data
            loadCurrentMappings(window.currentAuctionName, window.currentCompanyName);
            refreshRixoDropdowns();
        } else {
            showMessage('Failed to update mapping: ' + (data.message || 'Unknown error'), 'error');
        }
    })
    .catch(error => {
        console.error('Error updating mapping:', error);
        showMessage('Error updating mapping: ' + error.message, 'error');
    });
    
    window.editingMappingId = null;
};

// Cancel edit
window.cancelEdit = function(mappingId) {
    loadCurrentMappings(window.currentAuctionName, window.currentCompanyName);
    window.editingMappingId = null;
};

// Delete mapping
window.deleteMapping = function(mappingId) {
    if (!confirm('Are you sure you want to delete this mapping?')) {
        return;
    }
    
    fetch(apiUrl(`rixo/mappings/${mappingId}`), {
        method: 'DELETE'
    })
    .then(response => response.json())
    .then(data => {
        if (data.success) {
            showMessage('Mapping deleted successfully', 'success');
            loadCurrentMappings(window.currentAuctionName, window.currentCompanyName);
            refreshRixoDropdowns();
        } else {
            showMessage('Failed to delete mapping: ' + data.message, 'error');
        }
    })
    .catch(error => {
        console.error('Error deleting mapping:', error);
        showMessage('Error deleting mapping', 'error');
    });
};

// Clear mapping form
window.clearMappingForm = function() {
    document.getElementById('newVehicleType').value = '';
    document.getElementById('newStockLocation').value = '';
    document.getElementById('newRixoCompany').value = '';
    document.getElementById('newRixoPrice').value = '';
    document.getElementById('newVenueId').value = '';
};

// Setup modal event listeners
function setupModalEventListeners() {
    // Save Changes button
    const saveBtn = document.getElementById('saveMappings');
    if (saveBtn) {
        saveBtn.addEventListener('click', function() {
            // The "Save Changes" button doesn't need to do anything special
            // since mappings are saved immediately when "Add Mapping" is clicked
            showMessage('All changes have been saved automatically', 'success');
            closeMappingModal();
        });
    }
    
    // Cancel button
    const cancelBtn = document.getElementById('cancelMappings');
    if (cancelBtn) {
        cancelBtn.addEventListener('click', closeMappingModal);
    }
}

// Save Rixo mappings changes (Save Changes button handler)
window.saveRixoMappingsChanges = function() {
    const isNewSupplier = window.isCreatingNewSupplier === true;
    let supplierName = window.currentAuctionName || '';
    
    if (isNewSupplier) {
        const supplierNameInput = document.getElementById('newSupplierNameInput');
        supplierName = supplierNameInput ? supplierNameInput.value.trim() : '';
    }
    
    if (!supplierName) {
        showMessage('Supplier name is required. Please enter a supplier name.', 'error');
        return;
    }
    
    // Check if there's an unsaved inline row
    const isAddingNew = window.isAddingNewRixoMapping === true;
    if (isAddingNew) {
        // Save the inline row first
        saveNewRixoMappingRow(supplierName);
        // Wait a bit for the save to complete, then close modal
        setTimeout(() => {
            // Refresh supplier dropdown before closing (in case it's a new supplier)
            refreshSupplierDropdown();
            closeMappingModal();
        }, 500);
    } else {
        // Refresh supplier dropdown before closing (in case it's a new supplier)
        refreshSupplierDropdown();
        closeMappingModal();
    }
};

// Refresh supplier dropdown (add new supplier to dropdown if it was created)
window.refreshSupplierDropdown = function() {
    // Get supplier name from input field if it's a new supplier, otherwise from global storage
    const isNewSupplier = window.isCreatingNewSupplier === true;
    let supplierName = '';
    
    if (isNewSupplier) {
        const supplierNameInput = document.getElementById('newSupplierNameInput');
        supplierName = supplierNameInput ? supplierNameInput.value.trim() : '';
    } else {
        supplierName = window.currentAuctionName || '';
    }
    
    if (supplierName) {
        console.log('🔄 Adding supplier \'' + supplierName + '\' to dropdown lists');
        // Add supplier to dropdowns
        const auctionNameSelect = document.getElementById('auctionName');
        const editAuctionNameSelect = document.getElementById('editAuctionName');
        
        function addSupplierToSelect(select) {
            if (select) {
                // Check if option already exists
                const existingOption = select.querySelector('option[value="' + supplierName + '"]');
                if (!existingOption) {
                    // Create new option element
                    const option = document.createElement('option');
                    option.setAttribute('value', supplierName);
                    option.textContent = supplierName;
                    // Insert after "Select Supplier Name" option (first option)
                    if (select.options.length > 0) {
                        select.insertBefore(option, select.options[1]);
                    } else {
                        select.appendChild(option);
                    }
                    console.log('✅ Added supplier \'' + supplierName + '\' to dropdown');
                } else {
                    console.log('ℹ️ Supplier \'' + supplierName + '\' already exists in dropdown');
                }
            }
        }
        
        addSupplierToSelect(auctionNameSelect);
        addSupplierToSelect(editAuctionNameSelect);
    } else {
        console.log('⚠️ Cannot add supplier to dropdown - supplier name is blank');
    }
};

// Close modal
window.closeMappingModal = function() {
    const modal = document.getElementById('rixoMappingModal');
    modal.style.display = 'none';
    window.currentAuctionName = null;
    window.currentCompanyName = null;
    window.editingMappingId = null;
    window.isCreatingNewSupplier = false;
    
    // Ensure manage buttons are visible after closing modal
    if (typeof window.toggleManageButtons === 'function') {
        window.toggleManageButtons();
    }
};

// Refresh Rixo dropdowns after changes
function __isOnPurchaseFormPage() {
    var p = window.location.pathname || '';
    if (p.indexOf('/add') >= 0 || p.indexOf('/edit') >= 0) return true;
    return !!document.getElementById('auctionName') || !!document.getElementById('editAuctionName');
}

function __snapshotSupplierFormForPreserve() {
    if (!__isOnPurchaseFormPage()) return null;
    var isEdit = !!document.getElementById('editAuctionName');
    var auctionId = isEdit ? 'editAuctionName' : 'auctionName';
    var g = typeof window.getComboboxValue === 'function'
        ? function(id) { return (window.getComboboxValue(id) || '').trim(); }
        : function(id) {
            var inp = document.getElementById(id + 'Input');
            var sel = document.getElementById(id);
            return ((inp && inp.value) || (sel && sel.value) || '').trim();
        };
    return {
        isEdit: isEdit,
        auction: g(auctionId),
        stock: g(isEdit ? 'editStockLocation' : 'stockLocation'),
        venue: g(isEdit ? 'editVenueId' : 'venueId'),
        rixo: g(isEdit ? 'editRixoCompany' : 'rixoCompany'),
        pol: g(isEdit ? 'editPol' : 'pol'),
        vehicleType: g(isEdit ? 'editShipmentSize' : 'shipmentSize')
    };
}

function __restoreSupplierSubFieldsFromSnap(snap) {
    if (!snap) return;
    if (snap.auction && typeof window.rebuildSupplierDependentDropdowns === 'function') {
        window.rebuildSupplierDependentDropdowns(snap.auction, {
            autoSelect: false,
            preserveSnapshot: snap,
            restoreDelay: 50
        });
        return;
    }
    if (snap.stock) restoreSupplierMasterFieldValue('stockLocation', 'editStockLocation', snap.stock);
    if (snap.pol) restoreSupplierMasterFieldValue('pol', 'editPol', snap.pol);
    if (snap.venue) restoreSupplierMasterFieldValue('venueId', 'editVenueId', snap.venue);
    if (snap.rixo) restoreSupplierMasterFieldValue('rixoCompany', 'editRixoCompany', snap.rixo);
    if (snap.vehicleType) restoreSupplierMasterFieldValue('shipmentSize', 'editShipmentSize', snap.vehicleType);
    if (snap.stock && snap.auction && typeof window.fetchPolsByStockLocationAndUpdate === 'function') {
        var polHint = snap.pol ? [snap.pol] : null;
        window.fetchPolsByStockLocationAndUpdate(snap.auction, snap.stock, false, polHint).then(function() {
            if (snap.pol) restoreSupplierMasterFieldValue('pol', 'editPol', snap.pol);
        }).catch(function() {});
    }
}

function __clearSupplierMasterSyncFlags() {
    window.__purchaseFormSyncInProgress = false;
    window.__suppressSupplierModalFlow = false;
    window.__supplierSkipSilentAutoSelect = false;
}

function __restoreSupplierFormFromMasterSync(snap) {
    if (!snap) {
        __clearSupplierMasterSyncFlags();
        return;
    }
    var hasAuction = !!(snap.auction && String(snap.auction).trim());
    var hasSubFields = !!(snap.stock || snap.pol || snap.venue || snap.rixo || snap.vehicleType);
    if (!hasAuction && !hasSubFields) {
        __clearSupplierMasterSyncFlags();
        return;
    }
    window.__suppressSupplierModalFlow = true;
    window.__supplierSkipSilentAutoSelect = true;
    if (hasAuction) {
        var auction = String(snap.auction).trim();
        var auctionId = snap.isEdit ? 'editAuctionName' : 'auctionName';
        var aucSel = document.getElementById(auctionId);
        var aucInp = document.getElementById(auctionId + 'Input');
        if (aucSel) {
            var has = false;
            for (var i = 0; i < aucSel.options.length; i++) {
                if (aucSel.options[i].value === auction) { has = true; break; }
            }
            if (!has) {
                var o = document.createElement('option');
                o.value = auction;
                o.textContent = auction;
                aucSel.appendChild(o);
            }
            aucSel.value = auction;
            if (aucInp) aucInp.value = auction;
            if (typeof window.syncComboboxInput === 'function') window.syncComboboxInput(auctionId);
        }
        snap.auction = auction;
        if (typeof window.rebuildSupplierDependentDropdowns === 'function') {
            window.rebuildSupplierDependentDropdowns(auction, {
                autoSelect: false,
                preserveSnapshot: snap,
                restoreDelay: 100
            });
        } else if (typeof window.updateDropdownOptions === 'function') {
            window.updateDropdownOptions(auction);
        }
    } else if (hasSubFields) {
        __restoreSupplierSubFieldsFromSnap(snap);
    }
    setTimeout(__clearSupplierMasterSyncFlags, 400);
}

function __runPopulateThenAfter(fromMasterSync, preserveSnap) {
    var populatePromise = (typeof window.populateDropdownOptions === 'function')
        ? window.populateDropdownOptions()
        : Promise.resolve();
    Promise.resolve(populatePromise).then(function() {
        __afterRixoDropdownsPopulated(fromMasterSync, preserveSnap);
    }).catch(function(err) {
        console.warn('populateDropdownOptions failed during supplier refresh:', err);
        __afterRixoDropdownsPopulated(fromMasterSync, preserveSnap);
    });
}

function __afterRixoDropdownsPopulated(fromMasterSync, preserveSnap) {
    if (fromMasterSync) {
        if (preserveSnap && (preserveSnap.auction || preserveSnap.stock || preserveSnap.pol || preserveSnap.venue || preserveSnap.rixo || preserveSnap.vehicleType)) {
            __restoreSupplierFormFromMasterSync(preserveSnap);
        } else {
            __clearSupplierMasterSyncFlags();
        }
        if (typeof window.toggleManageButtons === 'function') window.toggleManageButtons();
        return;
    }
    if (document.getElementById('editForm') && window.__editPurchaseHydrating === true) {
        var editSnap = preserveSnap;
        if (!editSnap && typeof __snapshotSupplierFormForPreserve === 'function') {
            editSnap = __snapshotSupplierFormForPreserve();
        }
        if (!editSnap && window.__rixoSupplierPreserveSnapshot) {
            editSnap = window.__rixoSupplierPreserveSnapshot;
            if (editSnap) editSnap.isEdit = true;
        }
        if (editSnap && (editSnap.auction || editSnap.stock || editSnap.pol || editSnap.venue || editSnap.rixo || editSnap.vehicleType)) {
            __restoreSupplierFormFromMasterSync(editSnap);
        }
        if (typeof window.toggleManageButtons === 'function') window.toggleManageButtons();
        return;
    }
    const auctionNameSelect = document.getElementById('auctionName');
    const editAuctionNameSelect = document.getElementById('editAuctionName');
    let selectedAuctionName = null;
    if (auctionNameSelect && auctionNameSelect.value && auctionNameSelect.value !== '__add_new_supplier__') {
        selectedAuctionName = auctionNameSelect.value;
    } else if (editAuctionNameSelect && editAuctionNameSelect.value && editAuctionNameSelect.value !== '__add_new_supplier__') {
        selectedAuctionName = editAuctionNameSelect.value;
    }
    var liveSnap = (typeof __snapshotSupplierFormForPreserve === 'function') ? __snapshotSupplierFormForPreserve() : null;
    var preserveSnapMerged = preserveSnap || liveSnap || window.__rixoSupplierPreserveSnapshot;
    if (selectedAuctionName && preserveSnapMerged && (preserveSnapMerged.stock || preserveSnapMerged.rixo || preserveSnapMerged.venue || preserveSnapMerged.pol)) {
        preserveSnapMerged.auction = preserveSnapMerged.auction || selectedAuctionName;
        if (typeof window.rebuildSupplierDependentDropdowns === 'function') {
            window.rebuildSupplierDependentDropdowns(selectedAuctionName, {
                autoSelect: false,
                preserveSnapshot: preserveSnapMerged,
                restoreDelay: 0,
                keepPreserveSnapshot: true
            });
        } else {
            __restoreSupplierSubFieldsFromSnap(preserveSnapMerged);
        }
    } else if (selectedAuctionName && window.autoSelectRelatedFields &&
        window.__editPurchaseHydrating !== true && window.__suppressSupplierModalFlow !== true &&
        window.__supplierFlowMode !== 'page_load') {
        setTimeout(function() {
            if (window.__editPurchaseHydrating === true || window.__suppressSupplierModalFlow === true ||
                window.__supplierFlowMode === 'page_load') return;
            var snapNow = (typeof __snapshotSupplierFormForPreserve === 'function') ? __snapshotSupplierFormForPreserve() : null;
            if (snapNow && (snapNow.stock || snapNow.rixo)) return;
            window.autoSelectRelatedFields(selectedAuctionName, 'auctionHouse', selectedAuctionName);
        }, 100);
    }
    if (typeof window.toggleManageButtons === 'function') window.toggleManageButtons();
}

window.refreshRixoDropdowns = function(options) {
    options = options || {};
    if (window.__supplierApplyInFlight === true && options.fromMasterSync !== true) {
        return;
    }
    var fromMasterSync = options.fromMasterSync === true;
    var preserveSnap = null;
    if (fromMasterSync || __isOnPurchaseFormPage()) {
        preserveSnap = __snapshotSupplierFormForPreserve();
        if (preserveSnap && (preserveSnap.auction || preserveSnap.stock || preserveSnap.pol || preserveSnap.venue || preserveSnap.rixo || preserveSnap.vehicleType)) {
            window.__rixoSupplierPreserveSnapshot = preserveSnap;
        }
    }
    if (!fromMasterSync) {
        window.__rixoSupplierPreserveSnapshot = preserveSnap || null;
    }
    // Reload the mapping data from backend
    fetch(apiUrl('rixo/prices'))
        .then(response => {
            if (!response.ok) {
                console.error('Failed to fetch Rixo prices:', response.status, response.statusText);
                // If API fails, use the static mapping data that's already loaded
                if (typeof window.rixoPriceMapping !== 'undefined' && Object.keys(window.rixoPriceMapping).length > 0) {
                    console.log('Using existing static Rixo mapping data');
                    if (!preserveSnap) {
                        preserveSnap = __snapshotSupplierFormForPreserve();
                        if (preserveSnap && (preserveSnap.auction || preserveSnap.stock || preserveSnap.pol || preserveSnap.venue || preserveSnap.rixo || preserveSnap.vehicleType)) {
                            window.__rixoSupplierPreserveSnapshot = preserveSnap;
                        }
                    }
                    __runPopulateThenAfter(fromMasterSync, preserveSnap);
                } else {
                    console.error('No Rixo mapping data available');
                    __clearSupplierMasterSyncFlags();
                }
                return null;
            }
            return response.json();
        })
        .then(data => {
            if (data && data.success) {
                if (!preserveSnap) {
                    preserveSnap = __snapshotSupplierFormForPreserve();
                    if (preserveSnap && (preserveSnap.auction || preserveSnap.stock || preserveSnap.pol || preserveSnap.venue || preserveSnap.rixo || preserveSnap.vehicleType)) {
                        window.__rixoSupplierPreserveSnapshot = preserveSnap;
                    }
                }
                rebuildRixoMapping(data.data);
                __runPopulateThenAfter(fromMasterSync, preserveSnap);
            } else if (!data) {
                return;
            }
        })
        .catch(error => {
            console.error('Error refreshing dropdowns:', error);
            if (typeof window.rixoPriceMapping !== 'undefined' && Object.keys(window.rixoPriceMapping).length > 0) {
                console.log('Using existing static Rixo mapping data as fallback');
                if (!preserveSnap) {
                    preserveSnap = __snapshotSupplierFormForPreserve();
                    if (preserveSnap && (preserveSnap.auction || preserveSnap.stock || preserveSnap.pol || preserveSnap.venue || preserveSnap.rixo || preserveSnap.vehicleType)) {
                        window.__rixoSupplierPreserveSnapshot = preserveSnap;
                    }
                }
                __runPopulateThenAfter(fromMasterSync, preserveSnap);
            } else {
                __clearSupplierMasterSyncFlags();
            }
        });
};

/* splitMasterListTokens / flattenSemicolonValues: defined near top of file (after static mapping). */

/**
 * Expand one supplier_price row into atomic mapping rows so each dropdown option is a single value
 * (not "YAMAZAKI;KLC;LOGICO").
 *
 * When stock / POL / venue have multiple ';'-split branches (Supplier Map rows), those dimensions are
 * paired by index (shorter lists padded by repeating the last token). Vehicle type × rixo price still
 * combine per branch (Cartesian) so single-stock multi-company + multi-vehicle rows keep working.
 * Otherwise the legacy full Cartesian product is used (e.g. multiple Rixo companies on one yard).
 */
function expandSupplierPriceRowToMappings(price) {
    const vehicleTypeRaw = price.shipmentSize || price.supportedVehicleType || '';
    const typesSplit = window.splitMasterListTokens(vehicleTypeRaw);
    const stocksSplit = window.splitMasterListTokens(price.stockLocation);
    const companiesSplit = window.splitMasterListTokens(price.rixoCompany);
    const venuesSplit = window.splitMasterListTokens(price.venueId);
    let priceTokens = window.splitMasterListTokens(price.rixoPrice);
    const polsSplit = window.splitMasterListTokens(price.pol);

    function orSingleton(tokens, fallback) {
        if (tokens.length > 0) return tokens;
        const f = fallback != null ? String(fallback).trim() : '';
        return f ? [f] : [''];
    }

    const parallelBranches =
        stocksSplit.length > 1 ||
        polsSplit.length > 1 ||
        venuesSplit.length > 1;

    let types = orSingleton(typesSplit, vehicleTypeRaw);
    let stocks = orSingleton(stocksSplit, price.stockLocation);
    let companies = orSingleton(companiesSplit, price.rixoCompany);
    let venues = orSingleton(venuesSplit, price.venueId);
    let pols = orSingleton(polsSplit, price.pol);
    if (priceTokens.length === 0) {
        const rp = price.rixoPrice != null ? String(price.rixoPrice).trim() : '';
        priceTokens = rp ? [rp] : [''];
    }

    function padBranchList(arr, n) {
        if (n <= 0) return [];
        if (!arr || arr.length === 0) return Array(n).fill('');
        const out = arr.slice();
        while (out.length < n) {
            out.push(out[out.length - 1]);
        }
        return out.slice(0, n);
    }

    const out = [];

    if (!parallelBranches) {
        for (let ti = 0; ti < types.length; ti++) {
            for (let si = 0; si < stocks.length; si++) {
                for (let ci = 0; ci < companies.length; ci++) {
                    for (let vi = 0; vi < venues.length; vi++) {
                        for (let pi = 0; pi < priceTokens.length; pi++) {
                            for (let poli = 0; poli < pols.length; poli++) {
                                const t = (types[ti] || '').trim();
                                const s = (stocks[si] || '').trim();
                                const c = (companies[ci] || '').trim();
                                const v = (venues[vi] || '').trim();
                                const p = (priceTokens[pi] || '').trim();
                                const polVal = (pols[poli] || '').trim();
                                if (!t && !s && !c && !v && !p) continue;
                                out.push({
                                    typeOfVehicle: t,
                                    stockLocation: s,
                                    rixoCompany: c,
                                    rixoPrice: p,
                                    venueId: v,
                                    pol: polVal
                                });
                            }
                        }
                    }
                }
            }
        }
        return out;
    }

    const n = Math.max(stocks.length, pols.length, venues.length, companies.length);
    const stocksP = padBranchList(stocks, n);
    const polsP = padBranchList(pols, n);
    const venuesP = padBranchList(venues, n);
    const companiesP = padBranchList(companies, n);

    for (let i = 0; i < n; i++) {
        const s = (stocksP[i] || '').trim();
        const polVal = (polsP[i] || '').trim();
        const v = (venuesP[i] || '').trim();
        const c = (companiesP[i] || '').trim();
        for (let ti = 0; ti < types.length; ti++) {
            for (let pi = 0; pi < priceTokens.length; pi++) {
                const t = (types[ti] || '').trim();
                const p = (priceTokens[pi] || '').trim();
                if (!t && !s && !c && !v && !p) continue;
                out.push({
                    typeOfVehicle: t,
                    stockLocation: s,
                    rixoCompany: c,
                    rixoPrice: p,
                    venueId: v,
                    pol: polVal
                });
            }
        }
    }
    return out;
}

/** Expand API rows with ';'-joined cells into atomic rows for modal disambiguation. */
window.expandSupplierApiRowsForAutofill = function(rows) {
    if (!rows || !Array.isArray(rows)) return [];
    var out = [];
    rows.forEach(function(row) {
        var price = {
            stockLocation: row.stockLocation,
            rixoCompany: row.rixoCompany,
            venueId: row.venueId,
            pol: row.pol,
            shipmentSize: row.supportedVehicleType || row.shipmentSize || '',
            rixoPrice: row.rixoPrice
        };
        function hasSemicolon(v) {
            return v != null && String(v).indexOf(';') >= 0;
        }
        var needsExpand = hasSemicolon(price.stockLocation) || hasSemicolon(price.rixoCompany) ||
            hasSemicolon(price.venueId) || hasSemicolon(price.pol) ||
            hasSemicolon(price.shipmentSize) || hasSemicolon(price.rixoPrice);
        if (!needsExpand) {
            out.push(row);
            return;
        }
        expandSupplierPriceRowToMappings(price).forEach(function(m) {
            out.push({
                id: row.id,
                stockLocation: m.stockLocation,
                rixoCompany: m.rixoCompany,
                venueId: m.venueId,
                pol: m.pol,
                supportedVehicleType: m.typeOfVehicle,
                rixoPrice: m.rixoPrice
            });
        });
    });
    return out.length ? out : rows;
};

/** Merge freshly fetched API rows into client rixoPriceMapping for one auction. */
window.mergeSupplierApiRowsIntoRixoPriceMapping = function(auctionName, rows) {
    if (!auctionName || !rows || !Array.isArray(rows) || rows.length === 0) return;
    window.rixoPriceMapping = window.rixoPriceMapping || {};
    var key = resolveRixoMappingKey(auctionName);
    if (!key) key = String(auctionName).trim();

    window.rixoPriceMapping[key] = {
        typeOfVehicle: [],
        stockLocation: [],
        rixoCompany: [],
        rixoPrice: [],
        venueId: [],
        mappings: []
    };

    rows.forEach(function(row) {
        var price = {
            auctionHouse: key,
            stockLocation: row.stockLocation,
            rixoCompany: row.rixoCompany,
            venueId: row.venueId,
            pol: row.pol,
            shipmentSize: row.supportedVehicleType || row.shipmentSize || '',
            rixoPrice: row.rixoPrice
        };
        expandSupplierPriceRowToMappings(price).forEach(function(mapping) {
            window.rixoPriceMapping[key].mappings.push(mapping);
        });
    });

    var auction = window.rixoPriceMapping[key];
    auction.typeOfVehicle = window.getUniqueValuesCaseInsensitive(auction.mappings.map(function(m) { return m.typeOfVehicle; }).filter(function(t) { return t && String(t).trim() !== ''; }));
    auction.stockLocation = window.getUniqueValuesCaseInsensitive(auction.mappings.map(function(m) { return m.stockLocation; }).filter(function(s) { return s && String(s).trim() !== ''; }));
    auction.rixoCompany = window.getUniqueValuesCaseInsensitive(auction.mappings.map(function(m) { return m.rixoCompany; }).filter(function(c) { return c && String(c).trim() !== ''; }));
    auction.rixoPrice = window.getUniqueValuesCaseInsensitive(auction.mappings.map(function(m) { return m.rixoPrice; }).filter(function(p) { return p && String(p).trim() !== ''; }));
    auction.venueId = window.getUniqueValuesCaseInsensitive(auction.mappings.map(function(m) { return m.venueId; }).filter(function(v) { return v && String(v).trim() !== ''; }));
};

// Rebuild Rixo mapping object from backend data
window.rebuildRixoMapping = function(rixoPrices) {
    window.rixoPriceMapping = {};
    
    rixoPrices.forEach(price => {
        const auctionName = price.auctionHouse;
        
        if (!window.rixoPriceMapping[auctionName]) {
            window.rixoPriceMapping[auctionName] = {
                typeOfVehicle: [],
                stockLocation: [],
                rixoCompany: [],
                rixoPrice: [],
                venueId: [],
                mappings: []
            };
        }

        const expanded = expandSupplierPriceRowToMappings(price);
        expanded.forEach(function(mapping) {
            window.rixoPriceMapping[auctionName].mappings.push(mapping);
        });
    });
    
    // Update arrays (flatten ';'-joined cells so dropdowns match Supplier Map semantics)
    Object.keys(window.rixoPriceMapping).forEach(auctionName => {
        const auction = window.rixoPriceMapping[auctionName];
        auction.typeOfVehicle = window.getUniqueValuesCaseInsensitive(auction.mappings.map(m => m.typeOfVehicle).filter(t => t && String(t).trim() !== ''));
        auction.stockLocation = window.getUniqueValuesCaseInsensitive(auction.mappings.map(m => m.stockLocation).filter(s => s && String(s).trim() !== ''));
        auction.rixoCompany = window.getUniqueValuesCaseInsensitive(auction.mappings.map(m => m.rixoCompany).filter(c => c && String(c).trim() !== ''));
        auction.rixoPrice = window.getUniqueValuesCaseInsensitive(auction.mappings.map(m => m.rixoPrice).filter(p => p && String(p).trim() !== ''));
        auction.venueId = window.getUniqueValuesCaseInsensitive(auction.mappings.map(m => m.venueId).filter(v => v && String(v).trim() !== ''));
    });
};

// Show message helper function
window.showMessage = function(message, type = 'info') {
    // Create or update message element
    let messageEl = document.getElementById('rixoMessage');
    if (!messageEl) {
        messageEl = document.createElement('div');
        messageEl.id = 'rixoMessage';
        messageEl.style.cssText = `
            position: fixed;
            top: 20px;
            right: 20px;
            padding: 12px 20px;
            border-radius: 4px;
            color: white;
            font-weight: 500;
            z-index: 10000;
            max-width: 300px;
            word-wrap: break-word;
        `;
        document.body.appendChild(messageEl);
    }
    
    const colors = {
        success: '#28a745',
        error: '#dc3545',
        warning: '#ffc107',
        info: '#17a2b8'
    };
    
    messageEl.style.backgroundColor = colors[type] || colors.info;
    messageEl.textContent = message;
    messageEl.style.display = 'block';
    
    // Auto-hide after 3 seconds
    setTimeout(() => {
        messageEl.style.display = 'none';
    }, 3000);
};

/** Autofill Rixo Price from rixo_mapping lookup (no Calculate button). */
window.findRixoPriceFromSupplierSelection = function(sel, auctionName) {
    sel = sel || {};
    var matchSel = {
        stockLocation: sel.stockLocation || sel.stock || '',
        venueId: sel.venueId || sel.venue || '',
        pol: sel.pol || '',
        rixoCompany: sel.rixoCompany || sel.rixo || ''
    };
    var vehicleType = (sel.supportedVehicleType || sel.vehicleType || '').trim();
    var rows = window.__tempSupplierRows;
    if (rows && Array.isArray(rows) && typeof window.rowMatchesSupplierSelection === 'function') {
        for (var i = 0; i < rows.length; i++) {
            var row = rows[i];
            if (!window.rowMatchesSupplierSelection(row, matchSel)) continue;
            if (vehicleType) {
                var vts = window.splitSupplierSemicolonTokens(row.supportedVehicleType || row.shipmentSize || row.typeOfVehicle || '');
                if (vts.length) {
                    var u = vehicleType.toLowerCase();
                    var vtOk = false;
                    for (var j = 0; j < vts.length; j++) {
                        if (String(vts[j]).trim().toLowerCase() === u) { vtOk = true; break; }
                    }
                    if (!vtOk) continue;
                }
            }
            var price = row.rixoPrice;
            if (price != null && String(price).trim() !== '') return String(price).trim();
        }
    }
    var auction = (auctionName || '').trim();
    if (!auction || !window.rixoPriceMapping) return null;
    var normalized = typeof normalizeAuctionNameForMapping === 'function'
        ? normalizeAuctionNameForMapping(auction) : auction;
    var keys = Object.keys(window.rixoPriceMapping);
    var key = keys.find(function(k) { return k.toLowerCase() === normalized.toLowerCase(); }) || normalized;
    var mappings = window.rixoPriceMapping[key] && window.rixoPriceMapping[key].mappings;
    if (!mappings || !mappings.length) return null;
    for (var m = 0; m < mappings.length; m++) {
        var map = mappings[m];
        var fakeRow = {
            stockLocation: map.stockLocation,
            venueId: map.venueId,
            pol: map.pol,
            rixoCompany: map.rixoCompany,
            supportedVehicleType: map.typeOfVehicle || map.shipmentSize,
            rixoPrice: map.rixoPrice
        };
        if (!window.rowMatchesSupplierSelection(fakeRow, matchSel)) continue;
        if (vehicleType) {
            var mapVt = String(map.typeOfVehicle || map.shipmentSize || '').trim().toLowerCase();
            if (mapVt && mapVt !== vehicleType.toLowerCase()) continue;
        }
        if (map.rixoPrice != null && String(map.rixoPrice).trim() !== '') return String(map.rixoPrice).trim();
    }
    return null;
};

window.applyRixoPriceToInput = function(isEditForm, priceRaw, inputIdOverride) {
    if (!priceRaw) return false;
    var inputId = inputIdOverride || (isEditForm ? 'editRixoPriceInput' : 'rixoPriceInput');
    var input = document.getElementById(inputId);
    if (!input) return false;
    var numericValue = (typeof window.parseRixoPrice === 'function') ? window.parseRixoPrice(priceRaw) : String(priceRaw);
    if (!numericValue) numericValue = String(priceRaw).replace(/[¥,\s]/g, '').replace(/[^0-9.]/g, '');
    if (!numericValue) return false;
    window.__rixoPriceProgrammaticSet = true;
    input.value = numericValue;
    input.dispatchEvent(new Event('input', { bubbles: true }));
    input.dispatchEvent(new Event('change', { bubbles: true }));
    window.__rixoPriceProgrammaticSet = false;
    return true;
};

window.scheduleAutofillRixoPriceFromMapping = function(isEditForm, fields) {
    fields = fields || {};
    if (window.__rixoPriceAutofillTimer) clearTimeout(window.__rixoPriceAutofillTimer);
    window.__rixoPriceAutofillTimer = setTimeout(function() {
        window.__rixoPriceAutofillTimer = null;
        window.autofillRixoPriceFromMapping(isEditForm, { fields: fields, force: fields.force === true });
    }, fields.delay != null ? fields.delay : 180);
};

window.autofillRixoPriceFromMapping = function(isEditForm, options) {
    options = options || {};
    var fields = options.fields || {};
    var inputIdOverride = fields.inputId || null;
    var auctionNameId = fields.auctionNameId || (isEditForm ? 'editAuctionName' : 'auctionName');
    var stockLocationId = fields.stockLocationId || (isEditForm ? 'editStockLocation' : 'stockLocation');
    var rixoCompanyId = fields.rixoCompanyId || (isEditForm ? 'editRixoCompany' : 'rixoCompany');
    var vehicleTypeId = fields.vehicleTypeId || (isEditForm ? 'editShipmentSize' : 'shipmentSize');
    // Quick Purchase uses qp* combobox ids when filling price into qpRixoPrice
    if (inputIdOverride === 'qpRixoPrice') {
        auctionNameId = 'qpAuctionName';
        stockLocationId = 'qpStockLocation';
        rixoCompanyId = 'qpRixoCompany';
    }

    if (window.__editPurchaseHydrating === true && !options.force) return;
    if (window.__rixoPriceUserOverride === true && !options.force) return;

    var auctionName = (fields.auctionName || (window.getComboboxValue ? window.getComboboxValue(auctionNameId) : '') || '').toString().trim();
    var stockLocation = (fields.stockLocation || (window.getComboboxValue ? window.getComboboxValue(stockLocationId) : '') || '').toString().trim();
    var rixoCompany = (fields.rixoCompany || (window.getComboboxValue ? window.getComboboxValue(rixoCompanyId) : '') || '').toString().trim();
    var vehicleType = (fields.supportedVehicleType || fields.vehicleType ||
        (window.getComboboxValue ? window.getComboboxValue(vehicleTypeId) : '') || '').toString().trim();

    if (!auctionName || !stockLocation || !rixoCompany) return;

    var venueId = (fields.venueId != null ? fields.venueId : (window.getComboboxValue ? window.getComboboxValue(isEditForm ? 'editVenueId' : 'venueId') : '') || '').toString().trim();
    var pol = (fields.pol != null ? fields.pol : (window.getComboboxValue ? window.getComboboxValue(isEditForm ? 'editPol' : 'pol') : '') || '').toString().trim();
    var lookupSel = {
        stockLocation: stockLocation,
        venueId: venueId,
        pol: pol,
        rixoCompany: rixoCompany,
        supportedVehicleType: vehicleType
    };

    var cached = window.findRixoPriceFromSupplierSelection(lookupSel, auctionName);
    if (cached && window.applyRixoPriceToInput(isEditForm, cached, inputIdOverride)) return;

    function fetchLookup(vt) {
        var url = apiUrl('rixo-mapping/lookup?' +
            'auctionName=' + encodeURIComponent(auctionName) + '&' +
            'stockLocation=' + encodeURIComponent(stockLocation) + '&' +
            'rixoCompany=' + encodeURIComponent(rixoCompany) +
            (vt ? ('&supportedVehicleType=' + encodeURIComponent(vt)) : '')
        );
        return window.fetch(url)
            .then(function(resp) { return resp && resp.ok ? resp.json() : null; })
            .then(function(result) {
                if (!result || result.success !== true) return null;
                return result.data ? result.data.rixoPrice : null;
            });
    }

    var chain = vehicleType ? fetchLookup(vehicleType) : fetchLookup(null);
    chain.then(function(price) {
        if (price && window.applyRixoPriceToInput(isEditForm, price, inputIdOverride)) return;
        if (!vehicleType) return;
        return fetchLookup(null).then(function(fallbackPrice) {
            if (fallbackPrice) window.applyRixoPriceToInput(isEditForm, fallbackPrice, inputIdOverride);
        });
    }).catch(function(err) {
        console.warn('autofillRixoPriceFromMapping error:', err);
    });
};
window.calculateRixoPriceFromMapping = window.autofillRixoPriceFromMapping;

window.buildSupplierMapAutofillFields = function(isEditForm) {
    var g = typeof window.getComboboxValue === 'function'
        ? function(id) { return (window.getComboboxValue(id) || '').trim(); }
        : function() { return ''; };
    return {
        delay: 120,
        auctionName: g(isEditForm ? 'editAuctionName' : 'auctionName'),
        stockLocation: g(isEditForm ? 'editStockLocation' : 'stockLocation'),
        rixoCompany: g(isEditForm ? 'editRixoCompany' : 'rixoCompany'),
        venueId: g(isEditForm ? 'editVenueId' : 'venueId'),
        pol: g(isEditForm ? 'editPol' : 'pol'),
        supportedVehicleType: g(isEditForm ? 'editShipmentSize' : 'shipmentSize')
    };
};

window.onSupplierMapFieldChanged = function(fieldId) {
    if (typeof window.isSupplierMapProgrammaticUpdate === 'function' && window.isSupplierMapProgrammaticUpdate()) return;
    if (window.__suppressRixoAutoSelect === true || window.__editPurchaseHydrating === true) return;
    if (window.__supplierApplyInFlight === true) return;
    var isEdit = String(fieldId || '').indexOf('edit') === 0;
    var auctionId = isEdit ? 'editAuctionName' : 'auctionName';
    var auction = window.getComboboxValue ? window.getComboboxValue(auctionId) : '';
    if (!auction) return;
    var priceFields = typeof window.buildSupplierMapAutofillFields === 'function'
        ? window.buildSupplierMapAutofillFields(isEdit)
        : { delay: 120 };
    var snap = typeof __snapshotSupplierFormForPreserve === 'function' ? __snapshotSupplierFormForPreserve() : null;
    var shipmentOnly = fieldId === 'shipmentSize' || fieldId === 'editShipmentSize';
    if (shipmentOnly && typeof window.scheduleAutofillRixoPriceFromMapping === 'function') {
        window.scheduleAutofillRixoPriceFromMapping(isEdit, priceFields);
        return;
    }
    if (typeof window.rebuildSupplierDependentDropdowns === 'function') {
        window.rebuildSupplierDependentDropdowns(auction, {
            autoSelect: false,
            preserveSnapshot: snap,
            restoreDelay: 50,
            onRestored: function() {
                if (typeof window.scheduleAutofillRixoPriceFromMapping === 'function') {
                    window.scheduleAutofillRixoPriceFromMapping(isEdit, priceFields);
                }
            }
        });
    } else if (typeof window.scheduleAutofillRixoPriceFromMapping === 'function') {
        window.scheduleAutofillRixoPriceFromMapping(isEdit, priceFields);
    }
    if ((fieldId === 'stockLocation' || fieldId === 'editStockLocation') && auction && snap && snap.stock &&
        typeof window.fetchPolsByStockLocationAndUpdate === 'function') {
        var polHint = snap.pol ? [snap.pol] : [];
        window.fetchPolsByStockLocationAndUpdate(auction, snap.stock, false, polHint, window.__supplierMappingSeq || 0);
    }
};

window.wireSupplierMapCascadeAutofill = function() {
    var fieldIds = ['venueId', 'editVenueId', 'stockLocation', 'editStockLocation', 'pol', 'editPol',
        'rixoCompany', 'editRixoCompany', 'shipmentSize', 'editShipmentSize'];
    fieldIds.forEach(function(id) {
        var el = document.getElementById(id);
        if (el && !el.__supplierCascadeWired) {
            el.__supplierCascadeWired = true;
            el.addEventListener('change', function() { window.onSupplierMapFieldChanged(id); });
        }
        var inp = document.getElementById(id + 'Input');
        if (inp && !inp.__supplierCascadeWired) {
            inp.__supplierCascadeWired = true;
            inp.addEventListener('change', function() { window.onSupplierMapFieldChanged(id); });
        }
    });
    ['rixoPriceInput', 'editRixoPriceInput', 'qpRixoPrice'].forEach(function(priceId) {
        var priceInp = document.getElementById(priceId);
        if (priceInp && !priceInp.__rixoOverrideWired) {
            priceInp.__rixoOverrideWired = true;
            priceInp.addEventListener('input', function() {
                if (window.__rixoPriceProgrammaticSet === true) return;
                window.__rixoPriceUserOverride = true;
            });
        }
    });
};

// Initialize event listeners when DOM is ready
document.addEventListener('DOMContentLoaded', function() {
    // Modal close events
    const modal = document.getElementById('rixoMappingModal');
    const closeBtn = document.querySelector('.close');
    const cancelBtn = document.getElementById('cancelMappings');
    
    if (closeBtn) {
        closeBtn.addEventListener('click', closeMappingModal);
    }
    
    if (cancelBtn) {
        cancelBtn.addEventListener('click', closeMappingModal);
    }
    
    // Close modal when clicking outside
    if (modal) {
        modal.addEventListener('click', function(e) {
            if (e.target === modal) {
                closeMappingModal();
            }
        });
    }
    
    // Manage button click events (always visible, can open even with empty supplier)
    // Modal functionality removed - gear buttons now navigate to Supplier Master List
    // Event listeners for gear buttons are handled in MinimalPurchaseApp.kt
});