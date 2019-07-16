UPDATE users
SET password = CONCAT('{bcrypt}', password)
WHERE password NOT ilike '{bcrypt}%'
