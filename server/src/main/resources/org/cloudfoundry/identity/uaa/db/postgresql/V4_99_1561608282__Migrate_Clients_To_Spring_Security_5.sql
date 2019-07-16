UPDATE oauth_client_details
SET client_secret = CONCAT('{bcrypt}', client_secret)
WHERE client_secret NOT ilike '{bcrypt}%'
