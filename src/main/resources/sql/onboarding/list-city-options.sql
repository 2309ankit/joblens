SELECT name
FROM city
WHERE country_code = :countryCode
  AND (:query = '' OR ascii_name ILIKE :query || '%')
ORDER BY population DESC NULLS LAST, name
LIMIT 10
