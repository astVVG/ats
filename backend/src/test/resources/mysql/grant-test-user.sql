-- Tests only. Migration tests create throwaway databases named flyway_*; the underscore is
-- escaped so that it is not a wildcard.
GRANT ALL PRIVILEGES ON `flyway\_%`.* TO 'test'@'%';
