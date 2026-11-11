COPY (
    SELECT * FROM read_csv(${base}, delim = ';', header = true, all_varchar = true)
    UNION ALL
    SELECT * FROM read_csv(${incoming}, delim = ';', header = true, all_varchar = true)
) TO ${snapshot} (FORMAT CSV, DELIMITER ';', HEADER true);
