CREATE OR REPLACE PROCEDURE test.foo_too(IN x INT, OUT y DOUBLE) LANGUAGE JAVASCRIPT PARAMETER STYLE VARIABLES AS 'y = x;' NO SQL