-- Keep titles.search_vector in sync for full-text search (name weighted above synopsis).
CREATE FUNCTION titles_search_vector_update() RETURNS trigger AS $$
BEGIN
    NEW.search_vector :=
        setweight(to_tsvector('english', coalesce(NEW.name, '')), 'A') ||
        setweight(to_tsvector('english', coalesce(NEW.synopsis, '')), 'B');
    RETURN NEW;
END
$$ LANGUAGE plpgsql;

CREATE TRIGGER titles_search_vector_trigger
    BEFORE INSERT OR UPDATE OF name, synopsis ON titles
    FOR EACH ROW EXECUTE FUNCTION titles_search_vector_update();

UPDATE titles SET name = name;
