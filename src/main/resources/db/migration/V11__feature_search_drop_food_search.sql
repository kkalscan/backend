-- Catalog product search removed: app only supports text describe + photo scan.
DELETE FROM feature_search_items WHERE id = 'food_search';

UPDATE feature_search_items
SET subtitle = 'Добавить блюдо текстом',
    keywords = 'описать,текст,написать,сказать,без фото,рассказать,продукт,найти,добавить еду'
WHERE id = 'describe_food';
