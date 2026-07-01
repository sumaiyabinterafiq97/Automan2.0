-- Number plate registration office locations (Japanese prefecture / city offices).
-- Idempotent: safe to re-run if row already exists (uk_master_menu_field_name).
INSERT INTO master_menu (field_name, field_values)
VALUES (
    'number_place',
    '室蘭 (Muroran),札幌 (Sapporo),函館 (Hakodate),旭川 (Asahikawa),釧路 (Kushiro),帯広 (Obihiro),十勝 (Tokachi),北見 (Kitami),知床 (Shiretoko),苫小牧 (Tomakomai),青森 (Aomori),弘前 (Hirosaki),岩手 (Iwate),盛岡 (Morioka),平泉 (Hiraizumi),宮城 (Miyagi),仙台 (Sendai),八戸 (Hachinohe),秋田 (Akita),山形 (Yamagata),福島 (Fukushima),茨城 (Ibaraki),栃木 (Tochigi),群馬 (Gunma),埼玉 (Saitama),千葉 (Chiba),市川 (ICHIKAWA),東京 (Tokyo),神奈川 (Kanagawa),新潟 (Niigata),富山 (Toyama),石川 (Ishikawa),福井 (Fukui),山梨 (Yamanashi),長野 (Nagano),岐阜 (Gifu),静岡 (Shizuoka),愛知 (Aichi),三重 (Mie),伊勢志摩 (ISESHIMA),滋賀 (Shiga),京都 (Kyoto),大阪 (Osaka),兵庫 (Hyogo),奈良 (Nara),和歌山 (Wakayama),鳥取 (Tottori),島根 (Shimane),岡山 (Okayama),広島 (Hiroshima),山口 (Yamaguchi),徳島 (Tokushima),香川 (Kagawa),愛媛 (Ehime),高知 (Kochi),福岡 (Fukuoka),福山 (Fukuyama),北九州(KITA-KYUSHU),佐賀 (Saga),長崎 (Nagasaki),熊本 (Kumamoto),大分 (Oita),宮崎 (Miyazaki),鹿児島 (Kagoshima),沖縄 (Okinawa)'
)
ON DUPLICATE KEY UPDATE field_values = VALUES(field_values);
