// Dynamic Rixo Price Mapping Data
// Generated from import_rixo_data.sql with hierarchical filtering

// API Base URL - use relative path so nginx can proxy to backend
const API_BASE_URL = '/api';

// Helper function to get API URL
function apiUrl(path) {
    const cleanPath = path.startsWith('/') ? path.substring(1) : path;
    return `${API_BASE_URL}/${cleanPath}`;
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
window.fetchPolsByStockLocationAndUpdate = function(auctionHouse, stockLocation, setFirstValue, mappingPolTokens) {
    if (setFirstValue === undefined) setFirstValue = true;
    var polSelect = document.getElementById('pol');
    var editPolSelect = document.getElementById('editPol');
    if (!stockLocation || String(stockLocation).trim() === '') {
        console.log('[POL] Clearing POL (empty stock location)');
        if (typeof window.updateDropdown === 'function') {
            window.updateDropdown('pol', 'editPol', [], true);
        }
        if (polSelect) { polSelect.value = ''; var inp = document.getElementById('polInput'); if (inp) inp.value = ''; }
        if (editPolSelect) { editPolSelect.value = ''; var inp = document.getElementById('editPolInput'); if (inp) inp.value = ''; }
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
    console.log('[POL] Fetching POLs from rixo_prices for auctionHouse:', auctionHouse, 'stockLocation:', stockLocation, '->', url);
    return fetch(url)
        .then(function(r) {
            if (!r.ok) {
                console.warn('[POL] API responded with status:', r.status, r.statusText, 'for', url);
            }
            return r.json();
        })
        .then(function(res) {
            var prices = (res && res.data && Array.isArray(res.data)) ? res.data : [];
            // Filter by stockLocation then extract distinct non-empty POLs
            var list = [];
            var seen = {};
            prices.forEach(function(p) {
                var sl = (p && p.stockLocation != null) ? String(p.stockLocation).trim() : '';
                if (sl !== String(stockLocation).trim()) return;
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

/** Read stock / venue / rixo / POL from the active form (edit purchase vs add). */
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
            pol: g('editPol')
        };
    } else {
        vals = {
            stock: g('stockLocation'),
            venue: g('venueId'),
            rixo: g('rixoCompany'),
            pol: g('pol')
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
            pol: mer(vals.pol, snap.pol)
        };
    }
    return vals;
}

// Helper function to auto-select related fields
window.autoSelectRelatedFields = function(auctionName, changedField, changedValue) {
    console.log('autoSelectRelatedFields called:', auctionName, changedField, changedValue);
    
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
        // When auction house is selected, auto-select stockLocation and venueId if they're consistent
        const stockLocations = window.getUniqueValuesCaseInsensitive(mappings.map(m => m.stockLocation).filter(s => s && String(s).trim() !== ''));
        const venueIds = window.getUniqueValuesCaseInsensitive(mappings.map(m => m.venueId).filter(v => v && String(v).trim() !== ''));
        const rixoCompanies = window.getUniqueValuesCaseInsensitive(mappings.map(m => m.rixoCompany).filter(c => c && String(c).trim() !== ''));
        const shipmentSizes = window.getUniqueValuesCaseInsensitive(mappings.map(m => m.typeOfVehicle).filter(t => t && String(t).trim() !== ''));
        const rixoPrices = window.getUniqueValuesCaseInsensitive(mappings.map(m => m.rixoPrice).filter(p => p && String(p).trim() !== ''));
        const polTokensFromMapping = window.flattenPolTokensFromMappings ? window.flattenPolTokensFromMappings(mappings) : [];

        console.log('Auto-selecting for auction house:', {
            stockLocations: stockLocations,
            venueIds: venueIds,
            rixoCompanies: rixoCompanies,
            shipmentSizes: shipmentSizes,
            rixoPrices: rixoPrices,
            polTokensFromMapping: polTokensFromMapping
        });
        
        // Update dropdowns with all available options for both add and edit forms FIRST (supplier rows | sep | master)
        updateDropdown('stockLocation', 'editStockLocation', stockLocations, true);
        updateDropdown('venueId', 'editVenueId', venueIds, true);
        updateDropdown('rixoCompany', 'editRixoCompany', rixoCompanies, true);
        // POL: same pattern as Stock Location — mapping POL tokens first (then async merge with rixo_prices + master)
        if (polTokensFromMapping.length > 0) {
            updateDropdown('pol', 'editPol', polTokensFromMapping, true);
        } else {
            updateDropdown('pol', 'editPol', [], true);
        }
        // Intentionally do NOT update Vehicle type or Rixo Price from mapping.
        
        // THEN auto-select values after dropdowns are populated
        // Use setTimeout to ensure dropdowns are fully populated first
        // Preserve current combobox values when still valid for this supplier (fixes edit form snapping back to [0] after refresh or remapping).
        setTimeout(function() {
            var cur = __getCurrentSupplierFieldValues();
            var stockPick = stockLocations.length > 0 ? __pickPreservedOrFirst(stockLocations, cur.stock) : null;
            var venuePick = venueIds.length > 0 ? __pickPreservedOrFirst(venueIds, cur.venue) : null;
            var rixoPick = rixoCompanies.length > 0 ? __pickPreservedOrFirst(rixoCompanies, cur.rixo) : null;
            var polPick = polTokensFromMapping.length > 0 ? __pickPreservedOrFirst(polTokensFromMapping, cur.pol) : null;

            if (stockLocations.length > 0 && stockPick) {
                setFieldValue('stockLocation', 'editStockLocation', stockPick);
                if (polTokensFromMapping.length > 0 && typeof window.setFieldValue === 'function') {
                    var polToSet = polPick || polTokensFromMapping[0];
                    window.setFieldValue('pol', 'editPol', polToSet);
                }
                // Third arg false => do not overwrite POL with merged[0] after rixo_prices fetch when current POL is still valid in supplier map
                var keepPolAfterFetch = !!(cur.pol && __listContainsTokenCaseInsensitive(polTokensFromMapping, cur.pol));
                if (typeof window.fetchPolsByStockLocationAndUpdate === 'function') {
                    window.fetchPolsByStockLocationAndUpdate(normalizedAuctionName, stockPick, !keepPolAfterFetch, polTokensFromMapping);
                }
            } else if (polTokensFromMapping.length > 0) {
                var polOne = polPick || polTokensFromMapping[0];
                if (typeof window.setFieldValue === 'function') {
                    window.setFieldValue('pol', 'editPol', polOne);
                }
            }

            if (venueIds.length > 0 && venuePick) {
                setFieldValue('venueId', 'editVenueId', venuePick);
            }

            if (rixoCompanies.length > 0 && rixoPick) {
                setFieldValue('rixoCompany', 'editRixoCompany', rixoPick);
            }
            if (window.__rixoSupplierPreserveSnapshot) {
                window.__rixoSupplierPreserveSnapshot = null;
            }
        }, 100);
        
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
    
    if (!auctionName || !window.rixoPriceMapping[auctionName]) {
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
    rixoCompany: true, editRixoCompany: true,
    stockLocation: true, editStockLocation: true,
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
        'rixoCompany': 'master-menu/rixo_company', 'editRixoCompany': 'master-menu/rixo_company',
        'pol': 'master-menu/pol', 'editPol': 'master-menu/pol'
    };
    return map[selectId] || null;
}

function appendMasterAfterSeparatorForSupplierField(selectId) {
    var path = masterApiPathForSupplierField(selectId);
    if (!path) return;
    var url = (typeof window.apiUrl === 'function') ? window.apiUrl(path) : (typeof apiUrl !== 'undefined' ? apiUrl(path) : '');
    if (!url) return;
    fetch(url)
        .then(function(r) { return r && r.ok ? r.json() : []; })
        .then(function(raw) {
            var list = Array.isArray(raw) ? raw : [];
            var sel = document.getElementById(selectId);
            if (!sel) return;
            var seen = {};
            for (var i = 0; i < sel.options.length; i++) {
                var v = (sel.options[i].value || '').trim();
                if (v && v !== SUPPLIER_MASTER_SEP_VALUE && v !== SEE_MORE_VALUE && v !== '__SEE_LESS__') {
                    seen[v.toLowerCase()] = true;
                }
            }
            list.forEach(function(item) {
                var it = String(item).trim();
                if (!it) return;
                if (seen[it.toLowerCase()]) return;
                seen[it.toLowerCase()] = true;
                var opt = document.createElement('option');
                opt.value = it;
                opt.textContent = it;
                sel.appendChild(opt);
            });
            if (typeof window.syncComboboxInput === 'function') {
                window.syncComboboxInput(selectId);
            }
        })
        .catch(function(err) {
            console.warn('[supplier+master] Failed to load master for', selectId, err);
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
        window.syncComboboxInput(selectId);
    }
    appendMasterAfterSeparatorForSupplierField(selectId);
}

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
        
        if (editDropdown) {
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
        
        if (editDropdown) {
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
        if (editInputField) {
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
            window.syncComboboxInput(addFieldId);
        }
    }
    if (addInputField) {
        addInputField.value = value;
        console.log('✅ Set add input value:', addFieldId + 'Input', '=', value);
    }
    
    // Set edit form values
    if (editField) {
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
            window.syncComboboxInput(editFieldId);
        }
    }
    if (editInputField) {
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
        if (editInput && editFieldId === 'editRixoPrice') {
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
window.refreshRixoDropdowns = function() {
    window.__rixoSupplierPreserveSnapshot = null;
    // Reload the mapping data from backend
    fetch(apiUrl('rixo/prices'))
        .then(response => {
            if (!response.ok) {
                console.error('Failed to fetch Rixo prices:', response.status, response.statusText);
                // If API fails, use the static mapping data that's already loaded
                if (typeof window.rixoPriceMapping !== 'undefined' && Object.keys(window.rixoPriceMapping).length > 0) {
                    console.log('Using existing static Rixo mapping data');
                    var preserveSnapFail = document.getElementById('editAuctionName') ? __getCurrentSupplierFieldValues() : null;
                    window.__rixoSupplierPreserveSnapshot = preserveSnapFail;
                    if (typeof window.populateDropdownOptions === 'function') {
                        window.populateDropdownOptions();
                    }
                    const auctionNameSelectF = document.getElementById('auctionName');
                    const editAuctionNameSelectF = document.getElementById('editAuctionName');
                    let selectedAuctionNameF = null;
                    if (auctionNameSelectF && auctionNameSelectF.value && auctionNameSelectF.value !== '__add_new_supplier__') {
                        selectedAuctionNameF = auctionNameSelectF.value;
                    } else if (editAuctionNameSelectF && editAuctionNameSelectF.value && editAuctionNameSelectF.value !== '__add_new_supplier__') {
                        selectedAuctionNameF = editAuctionNameSelectF.value;
                    }
                    if (selectedAuctionNameF && window.autoSelectRelatedFields) {
                        setTimeout(function() {
                            window.autoSelectRelatedFields(selectedAuctionNameF, 'auctionHouse', selectedAuctionNameF);
                        }, 100);
                    }
                    setTimeout(function() {
                        if (window.__rixoSupplierPreserveSnapshot) {
                            window.__rixoSupplierPreserveSnapshot = null;
                        }
                    }, 500);
                } else {
                    console.error('No Rixo mapping data available');
                }
                return null;
            }
            return response.json();
        })
        .then(data => {
            if (data && data.success) {
                // Snapshot edit form supplier fields BEFORE populateDropdownOptions clears innerHTML (stock/venue/rixo/pol)
                var preserveSnap = document.getElementById('editAuctionName') ? __getCurrentSupplierFieldValues() : null;
                window.__rixoSupplierPreserveSnapshot = preserveSnap;
                // Rebuild the mapping object
                rebuildRixoMapping(data.data);
                // Repopulate dropdowns
                if (typeof window.populateDropdownOptions === 'function') {
                    window.populateDropdownOptions();
                }
                
                // Trigger auto-selection for currently selected supplier (if any)
                // Check both add form and edit form
                const auctionNameSelect = document.getElementById('auctionName');
                const editAuctionNameSelect = document.getElementById('editAuctionName');
                
                let selectedAuctionName = null;
                if (auctionNameSelect && auctionNameSelect.value && auctionNameSelect.value !== '__add_new_supplier__') {
                    selectedAuctionName = auctionNameSelect.value;
                } else if (editAuctionNameSelect && editAuctionNameSelect.value && editAuctionNameSelect.value !== '__add_new_supplier__') {
                    selectedAuctionName = editAuctionNameSelect.value;
                }
                
                // If a supplier is selected, trigger auto-selection to populate fields
                if (selectedAuctionName && window.autoSelectRelatedFields) {
                    console.log('🔄 Triggering auto-selection for supplier after refresh:', selectedAuctionName);
                    // Small delay to ensure dropdowns are populated first
                    setTimeout(() => {
                        window.autoSelectRelatedFields(selectedAuctionName, 'auctionHouse', selectedAuctionName);
                    }, 100);
                }
                // Safety: clear snapshot if autoSelect exited early (no mapping rows) or after async chain
                setTimeout(function() {
                    if (window.__rixoSupplierPreserveSnapshot) {
                        window.__rixoSupplierPreserveSnapshot = null;
                    }
                }, 500);
                
                // Trigger auto-selection if needed
                window.toggleManageButtons();
            } else if (!data) {
                // API failed, but we already handled it above
                return;
            }
        })
        .catch(error => {
            console.error('Error refreshing dropdowns:', error);
            // Fallback to static data if available
            if (typeof window.rixoPriceMapping !== 'undefined' && Object.keys(window.rixoPriceMapping).length > 0) {
                console.log('Using existing static Rixo mapping data as fallback');
                var preserveSnapCatch = document.getElementById('editAuctionName') ? __getCurrentSupplierFieldValues() : null;
                window.__rixoSupplierPreserveSnapshot = preserveSnapCatch;
                if (typeof window.populateDropdownOptions === 'function') {
                    window.populateDropdownOptions();
                }
                const auctionNameSelectC = document.getElementById('auctionName');
                const editAuctionNameSelectC = document.getElementById('editAuctionName');
                let selectedAuctionNameC = null;
                if (auctionNameSelectC && auctionNameSelectC.value && auctionNameSelectC.value !== '__add_new_supplier__') {
                    selectedAuctionNameC = auctionNameSelectC.value;
                } else if (editAuctionNameSelectC && editAuctionNameSelectC.value && editAuctionNameSelectC.value !== '__add_new_supplier__') {
                    selectedAuctionNameC = editAuctionNameSelectC.value;
                }
                if (selectedAuctionNameC && window.autoSelectRelatedFields) {
                    setTimeout(function() {
                        window.autoSelectRelatedFields(selectedAuctionNameC, 'auctionHouse', selectedAuctionNameC);
                    }, 100);
                }
                setTimeout(function() {
                    if (window.__rixoSupplierPreserveSnapshot) {
                        window.__rixoSupplierPreserveSnapshot = null;
                    }
                }, 500);
            }
        });
};

/* splitMasterListTokens / flattenSemicolonValues: defined near top of file (after static mapping). */

/**
 * Expand one supplier_price row into atomic mapping rows so each dropdown option is a single value
 * (not "YAMAZAKI;KLC;LOGICO"). Cartesian product across split dimensions; same rixo price/venue repeated.
 */
function expandSupplierPriceRowToMappings(price) {
    let types = window.splitMasterListTokens(price.shipmentSize);
    let stocks = window.splitMasterListTokens(price.stockLocation);
    let companies = window.splitMasterListTokens(price.rixoCompany);
    let venues = window.splitMasterListTokens(price.venueId);
    let priceTokens = window.splitMasterListTokens(price.rixoPrice);
    let pols = window.splitMasterListTokens(price.pol);

    function orSingleton(tokens, fallback) {
        if (tokens.length > 0) return tokens;
        const f = fallback != null ? String(fallback).trim() : '';
        return f ? [f] : [''];
    }

    types = orSingleton(types, price.shipmentSize);
    stocks = orSingleton(stocks, price.stockLocation);
    companies = orSingleton(companies, price.rixoCompany);
    venues = orSingleton(venues, price.venueId);
    pols = orSingleton(pols, price.pol);
    if (priceTokens.length === 0) {
        const rp = price.rixoPrice != null ? String(price.rixoPrice).trim() : '';
        priceTokens = rp ? [rp] : [''];
    }

    const out = [];
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