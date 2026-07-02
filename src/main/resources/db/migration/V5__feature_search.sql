CREATE TABLE feature_search_items (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    subtitle TEXT,
    keywords TEXT NOT NULL,
    deeplink TEXT NOT NULL,
    icon TEXT NOT NULL DEFAULT 'default',
    sort_order INTEGER NOT NULL DEFAULT 0,
    enabled INTEGER NOT NULL DEFAULT 1,
    locale TEXT NOT NULL DEFAULT 'ru'
);

CREATE INDEX idx_feature_search_locale ON feature_search_items(locale, enabled, sort_order);

INSERT INTO feature_search_items (id, title, subtitle, keywords, deeplink, icon, sort_order) VALUES
('diary_today', 'Сегодня', 'Дневник питания и калории за день', 'сегодня,дневник,калории,ккал,день,съедено,питание', 'kkalscan://diary', 'today', 10),
('scan', 'Сканировать еду', 'Калории и БЖУ по фото', 'скан,фото,камера,распознать,добавить,еда,сфотографировать', 'kkalscan://scan', 'scan', 5),
('food_search', 'Найти продукт', 'Добавить блюдо из каталога', 'продукт,каталог,найти,борщ,творог,добавить еду,без фото', 'kkalscan://food-search', 'search', 15),
('journal', 'Дневник за неделю', 'Графики калорий и БЖУ', 'дневник,неделя,график,статистика,калории,прогресс,журнал', 'kkalscan://journal', 'journal', 20),
('fiber', 'Клетчатка', 'График клетчатки за неделю', 'клетчатка,график,кл,волокна,клетчатки', 'kkalscan://journal/fiber', 'fiber', 25),
('profile', 'Профиль', 'Подписка Pro и настройки', 'профиль,настройки,pro,подписка,аккаунт,личный', 'kkalscan://profile', 'profile', 30),
('paywall', 'Pro подписка', 'Безлимитные сканы — 199 ₽/мес', 'pro,подписка,безлимит,199,оплата,лимит', 'kkalscan://paywall', 'pro', 40),
('macros', 'БЖУ за неделю', 'Белки, жиры и углеводы', 'бжу,белки,жиры,углеводы,макросы,белок', 'kkalscan://journal', 'macros', 22),
('free_scans', 'Бесплатные сканы', '3 скана каждый день', 'сканы,бесплатно,лимит,осталось,бесплатный', 'kkalscan://diary', 'gift', 12),
('dietitian', 'Анализ диетолога', 'AI-разбор питания за неделю', 'диетолог,анализ,разбор,ai,insight,рекомендации', 'kkalscan://journal/dietitian', 'dietitian', 35);
