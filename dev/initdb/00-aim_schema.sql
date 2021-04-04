--
-- PostgreSQL database dump
--

-- Dumped from database version 11.10
-- Dumped by pg_dump version 11.10

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: public; Type: SCHEMA; Schema: -; Owner: -
--

-- CREATE SCHEMA public;


--
-- Name: SCHEMA public; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON SCHEMA public IS 'standard public schema';


--
-- Name: container_job_type; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.container_job_type AS ENUM (
    'copy',
    'move',
    'promote',
    'verify',
    'repair',
    'rtest',
    'runwaylifecycle'
);


--
-- Name: fsnode_archive_format; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.fsnode_archive_format AS ENUM (
    'zip',
    'gzip'
);


--
-- Name: fsnode_encryption; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.fsnode_encryption AS ENUM (
    'none',
    'aes256cbc'
);


--
-- Name: mpps_state; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.mpps_state AS ENUM (
    'IN PROGRESS',
    'DISCONTINUED',
    'COMPLETED'
);


--
-- Name: order_state; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.order_state AS ENUM (
    'A',
    'CA',
    'CM',
    'DC',
    'ER',
    'HD',
    'IP',
    'RP',
    'SC'
);


--
-- Name: order_state_old; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.order_state_old AS ENUM (
    'SC',
    'NW',
    'A',
    'CA',
    'CM',
    'ER'
);


--
-- Name: patient_sort_mode; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.patient_sort_mode AS ENUM (
    'alphabetical',
    'chronological',
    'manual'
);


--
-- Name: send_process_state; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.send_process_state AS ENUM (
    'initialized',
    'pending',
    'success',
    'ignored',
    'error',
    'stopped'
);


--
-- Name: snapshot_type; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.snapshot_type AS ENUM (
    'snapshot',
    'history',
    'favorite'
);


--
-- Name: storage_rule_node_type; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.storage_rule_node_type AS ENUM (
    'master',
    'replica',
    'delete'
);


--
-- Name: storage_rule_type; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.storage_rule_type AS ENUM (
    'import',
    'copy',
    'delete',
    'prefetch'
);


--
-- Name: uid_chain_type; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.uid_chain_type AS ENUM (
    'study',
    'series',
    'image'
);


--
-- Name: get_serial_number(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.get_serial_number() RETURNS integer
    LANGUAGE sql
    AS $$ SELECT serial_number FROM aim_db_version; $$;


--
-- Name: hex2dec(character varying); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.hex2dec(character varying) RETURNS character varying
    LANGUAGE plpgsql IMMUTABLE
    AS $_$
DECLARE 
	_x numeric;
	_count int;
	_digit int;
BEGIN
	_x := 0;
	for _count in 1..length($1) loop 
		EXECUTE E'SELECT x\''||substring($1 from _count for 1)|| E'\'::integer' INTO _digit;
		_x := _x * 16 + _digit ;
	end loop;
	return _x::varchar;
end
;
$_$;


--
-- Name: orgunit_check_no_cycles(bigint, bigint); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.orgunit_check_no_cycles(orgunit_id bigint, parent_id bigint) RETURNS boolean
    LANGUAGE plpgsql STABLE
    AS $$ BEGIN RETURN (WITH RECURSIVE parents AS (SELECT orgunit_id AS id, parent_id AS orgunit_fk, ARRAY[orgunit_id] AS path, false AS cycle UNION ALL SELECT orgunit.id, orgunit.orgunit_fk, parents.path || orgunit.id, orgunit.id = ANY(path) AS cycle FROM public.orgunit JOIN parents ON orgunit.id = parents.orgunit_fk WHERE NOT cycle) SELECT NOT bool_or(cycle) FROM parents); END; $$;


--
-- Name: synedra_kw_delete(bigint, bigint); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.synedra_kw_delete(keyword_id bigint, document_id bigint) RETURNS void
    LANGUAGE plpgsql
    AS $$
DECLARE

  counti integer;  

BEGIN

  IF (keyword_id IS NOT NULL AND keyword_id::text <> '')
   AND (document_id IS NOT NULL AND document_id::text <> '') THEN

    UPDATE link_document_keyword_all
    SET usage_cnt = usage_cnt - 1
    WHERE document_fk = document_id
     AND keyword_fk = keyword_id;

    SELECT usage_cnt
    INTO counti
    FROM link_document_keyword_all
    WHERE document_fk = document_id
     AND keyword_fk = keyword_id;

    IF counti <= 0 THEN

      DELETE FROM link_document_keyword_all
      WHERE document_fk = document_id
       AND keyword_fk = keyword_id;
    END IF;

  END IF;

END;
$$;


--
-- Name: synedra_kw_insert(bigint, bigint); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.synedra_kw_insert(keyword_id bigint, document_id bigint) RETURNS void
    LANGUAGE plpgsql
    AS $$ BEGIN INSERT INTO link_document_keyword_all(document_fk,   keyword_fk,   usage_cnt) VALUES(document_id, keyword_id, 1) ON CONFLICT ON CONSTRAINT link_document_keyword_all_pkey DO UPDATE SET usage_cnt = link_document_keyword_all.usage_cnt + 1; END; $$;


--
-- Name: synedra_last_contact(bigint); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.synedra_last_contact(patientid bigint) RETURNS timestamp without time zone
    LANGUAGE plpgsql COST 1000
    AS $$ BEGIN 	RETURN MAX(lc) as da FROM ( 		SELECT max(coalesce(coalesce(visit.discharge_date_time, visit.admit_date_time), visit.inserted_when)) AS lc 		FROM visit 		JOIN link_patient_visit ON link_patient_visit.visit_fk = visit.id AND link_patient_visit.patient_fk = patientid 		UNION ALL 		SELECT max(coalesce(document.document_created_when, document.inserted_when)) FROM document 		JOIN link_patient_visit ON link_patient_visit.id = document.link_patient_visit_fk AND link_patient_visit.patient_fk = patientid 	) AS sub; END; $$;


--
-- Name: synedra_md5(text); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.synedra_md5(str text) RETURNS character varying
    LANGUAGE plpgsql
    AS $$
BEGIN
  IF coalesce(str::text, '') = '' THEN
    RETURN 'D41D8CD98F00B204E9800998ECF8427E';
  ELSE 
    RETURN upper(md5(str::text));
  END IF;
END;
$$;


--
-- Name: synedra_observation_get_quantity_value_range(numeric, text); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.synedra_observation_get_quantity_value_range(quantity numeric, comparator text) RETURNS numrange
    LANGUAGE plpgsql IMMUTABLE COST 10
    AS $$ BEGIN IF comparator = '<' THEN RETURN numrange(NULL, quantity, '()'); ELSIF comparator = '<=' THEN RETURN numrange(NULL, quantity, '(]'); ELSIF comparator = '>' THEN RETURN numrange(quantity, NULL, '()'); ELSIF comparator = '>=' THEN RETURN numrange(quantity, NULL, '[)'); ELSE RETURN numrange(quantity, quantity, '[]'); END IF; END; $$;


--
-- Name: trigger_fct_tai_ar_file(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.trigger_fct_tai_ar_file() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  runwayAvail integer;
BEGIN
  SELECT count(*) INTO runwayAvail 
  FROM ar_file_summary 
  WHERE runway_name = new.runway_name;
  IF runwayAvail = 0 THEN
    INSERT INTO ar_file_summary (runway_name,bytes_total,bytes_online) 
      VALUES (new.runway_name,new.file_size,(new.file_size * CAST(new.is_online AS integer)));
  ELSE
    UPDATE ar_file_summary 
      SET bytes_total = bytes_total+new.file_size,
          bytes_online= bytes_online+(new.file_size * CAST(new.is_online AS integer))
    WHERE runway_name = new.runway_name;
  END IF;
  RETURN new;
END;
     
$$;


--
-- Name: trigger_fct_tai_link_tn_tiv_fao(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.trigger_fct_tai_link_tn_tiv_fao() RETURNS trigger
    LANGUAGE plpgsql
    AS $$ DECLARE runwayAvail integer; BEGIN SELECT count(*) INTO runwayAvail FROM fsnode_archive_object_summary WHERE tbl_node_fk = new.tbl_node_fk; IF runwayAvail = 0 THEN INSERT INTO fsnode_archive_object_summary (tbl_node_fk, bytes_total, bytes_online) SELECT new.tbl_node_fk , tbl_item_version.byte_size , tbl_item_version.byte_size * CAST(fsnode_archive_object.is_online AS integer) FROM tbl_item_version, fsnode_archive_object WHERE new.tbl_item_version_fk = tbl_item_version.id AND new.fsnode_archive_object_fk = fsnode_archive_object.id; ELSE UPDATE fsnode_archive_object_summary SET bytes_total = bytes_total + tbl_item_version.byte_size, bytes_online = bytes_online + (tbl_item_version.byte_size * CAST(fsnode_archive_object.is_online AS integer)) FROM tbl_item_version, fsnode_archive_object WHERE new.tbl_item_version_fk = tbl_item_version.id AND new.fsnode_archive_object_fk = fsnode_archive_object.id AND new.tbl_node_fk = tbl_node_fk; END IF; RETURN new; END; $$;


--
-- Name: trigger_fct_taiu_ldikw_update_ldkwa(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.trigger_fct_taiu_ldikw_update_ldkwa() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  document_id bigint;
BEGIN
  IF (TG_OP = 'UPDATE') THEN
      SELECT ds.dicom_study_fk INTO document_id FROM dicom_series ds 
            JOIN dicom_image di ON di.dicom_series_fk=ds.id
            WHERE di.id=old.dicom_image_fk; 
      PERFORM synedra_kw_delete(old.keyword_fk, document_id);
  END IF;
  SELECT ds.dicom_study_fk INTO document_id FROM dicom_series ds 
        JOIN dicom_image di ON di.dicom_series_fk=ds.id
        WHERE di.id=new.dicom_image_fk;
  PERFORM synedra_kw_insert(new.keyword_fk, document_id);
  RETURN NEW;
END
$$;


--
-- Name: trigger_fct_taiu_ldkw_update_ldkwa(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.trigger_fct_taiu_ldkw_update_ldkwa() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  document_id bigint;
BEGIN
  IF (TG_OP = 'UPDATE') THEN
      document_id := old.document_fk;
      PERFORM synedra_kw_delete(old.keyword_fk, document_id);
  END IF;
  document_id := new.document_fk;
  PERFORM synedra_kw_insert(new.keyword_fk, document_id);
  RETURN NEW;
END
$$;


--
-- Name: trigger_fct_taiu_ldskw_update_ldkwa(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.trigger_fct_taiu_ldskw_update_ldkwa() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  document_id bigint;
BEGIN
  IF (TG_OP = 'UPDATE') THEN
      SELECT ds.dicom_study_fk INTO document_id FROM dicom_series ds WHERE ds.id=old.dicom_series_fk;
      PERFORM synedra_kw_delete(old.keyword_fk, document_id);
  END IF;
  SELECT ds.dicom_study_fk INTO document_id FROM dicom_series ds WHERE ds.id=new.dicom_series_fk;
  PERFORM synedra_kw_insert(new.keyword_fk, document_id);
  RETURN NEW;
END
$$;


--
-- Name: trigger_fct_taiu_lgfkw_update_ldkwa(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.trigger_fct_taiu_lgfkw_update_ldkwa() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  document_id bigint;
BEGIN
  IF (TG_OP = 'UPDATE') THEN
      SELECT gf.generic_container_fk INTO document_id FROM generic_file gf
            WHERE gf.id=old.generic_file_fk;
      PERFORM synedra_kw_delete(old.keyword_fk, document_id);
  END IF;
  SELECT gf.generic_container_fk INTO document_id FROM generic_file gf
        WHERE gf.id=new.generic_file_fk;
  PERFORM synedra_kw_insert(new.keyword_fk, document_id);
 
  RETURN NEW;
END;
$$;


--
-- Name: trigger_fct_tau_ar_file(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.trigger_fct_tau_ar_file() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  runwayAvail integer;
BEGIN
  SELECT count(*) INTO runwayAvail 
  FROM ar_file_summary 
  WHERE runway_name = new.runway_name;
  
  UPDATE ar_file_summary 
    SET bytes_total = bytes_total - old.file_size,
        bytes_online= bytes_online - (old.file_size * CAST(old.is_online AS integer))
  WHERE runway_name = old.runway_name;
  
  IF runwayAvail = 0 THEN
    INSERT INTO ar_file_summary (runway_name,bytes_total,bytes_online) 
      VALUES (new.runway_name,new.file_size,(new.file_size * CAST(new.is_online AS integer)));
  ELSE
    UPDATE ar_file_summary 
      SET bytes_total = bytes_total + new.file_size,
          bytes_online= bytes_online + (new.file_size * CAST(new.is_online AS integer))
    WHERE runway_name = new.runway_name;
  END IF;
  RETURN new;
END;
     
$$;


--
-- Name: trigger_fct_tau_fsnode_archive_object(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.trigger_fct_tau_fsnode_archive_object() RETURNS trigger
    LANGUAGE plpgsql
    AS $$ DECLARE runwayAvail integer; BEGIN SELECT count(*) INTO runwayAvail FROM fsnode_archive_object_summary JOIN link_tbl_node_tbl_item_version_fsnode_archive_object ON fsnode_archive_object_summary.tbl_node_fk = link_tbl_node_tbl_item_version_fsnode_archive_object.tbl_node_fk WHERE new.id = fsnode_archive_object_fk; IF old.id <> new.id THEN WITH item_version_byte_sizes AS ( SELECT tbl_item_version.byte_size AS byte_size , link_tbl_node_tbl_item_version_fsnode_archive_object.tbl_node_fk AS tbl_node_fk FROM tbl_item_version JOIN link_tbl_node_tbl_item_version_fsnode_archive_object ON tbl_item_version_fk = tbl_item_version.id WHERE old.id = fsnode_archive_object_fk ) UPDATE fsnode_archive_object_summary SET bytes_total = bytes_total - ( SELECT coalesce(sum(item_version_byte_sizes.byte_size), 0) FROM item_version_byte_sizes WHERE item_version_byte_sizes.tbl_node_fk = fsnode_archive_object_summary.tbl_node_fk ) WHERE EXISTS ( SELECT 1 FROM item_version_byte_sizes WHERE item_version_byte_sizes.tbl_node_fk = fsnode_archive_object_summary.tbl_node_fk ); END IF; IF old.id <> new.id OR old.is_online <> new.is_online THEN WITH item_version_byte_sizes AS ( SELECT tbl_item_version.byte_size AS byte_size , link_tbl_node_tbl_item_version_fsnode_archive_object.tbl_node_fk AS tbl_node_fk FROM tbl_item_version JOIN link_tbl_node_tbl_item_version_fsnode_archive_object ON tbl_item_version_fk = tbl_item_version.id WHERE old.id = fsnode_archive_object_fk AND old.is_online ) UPDATE fsnode_archive_object_summary SET bytes_online = bytes_online - ( SELECT coalesce(sum(item_version_byte_sizes.byte_size), 0) FROM item_version_byte_sizes WHERE item_version_byte_sizes.tbl_node_fk = fsnode_archive_object_summary.tbl_node_fk ) WHERE EXISTS ( SELECT 1 FROM item_version_byte_sizes WHERE item_version_byte_sizes.tbl_node_fk = fsnode_archive_object_summary.tbl_node_fk ); END IF; IF runwayAvail = 0 THEN INSERT INTO fsnode_archive_object_summary (tbl_node_fk, bytes_total, bytes_online) SELECT link_tbl_node_tbl_item_version_fsnode_archive_object.tbl_node_fk, coalesce(sum(tbl_item_version.byte_size), 0), coalesce(sum(tbl_item_version.byte_size), 0) * CAST(new.is_online AS integer) FROM tbl_item_version JOIN link_tbl_node_tbl_item_version_fsnode_archive_object ON tbl_item_version_fk = tbl_item_version.id WHERE fsnode_archive_object_fk = new.id GROUP BY tbl_node_fk; ELSE IF old.id <> new.id THEN WITH item_version_byte_sizes AS ( SELECT tbl_item_version.byte_size AS byte_size , link_tbl_node_tbl_item_version_fsnode_archive_object.tbl_node_fk AS tbl_node_fk FROM tbl_item_version JOIN link_tbl_node_tbl_item_version_fsnode_archive_object ON tbl_item_version_fk = tbl_item_version.id WHERE new.id = fsnode_archive_object_fk ) UPDATE fsnode_archive_object_summary SET bytes_total = bytes_total + ( SELECT coalesce(sum(item_version_byte_sizes.byte_size), 0) FROM item_version_byte_sizes WHERE item_version_byte_sizes.tbl_node_fk = fsnode_archive_object_summary.tbl_node_fk ) WHERE EXISTS ( SELECT 1 FROM item_version_byte_sizes WHERE item_version_byte_sizes.tbl_node_fk = fsnode_archive_object_summary.tbl_node_fk ); END IF; IF old.id <> new.id OR old.is_online <> new.is_online THEN WITH item_version_byte_sizes AS ( SELECT tbl_item_version.byte_size AS byte_size , link_tbl_node_tbl_item_version_fsnode_archive_object.tbl_node_fk AS tbl_node_fk FROM tbl_item_version JOIN link_tbl_node_tbl_item_version_fsnode_archive_object ON tbl_item_version_fk = tbl_item_version.id WHERE new.id = fsnode_archive_object_fk AND new.is_online ) UPDATE fsnode_archive_object_summary SET bytes_online = bytes_online + ( SELECT coalesce(sum(item_version_byte_sizes.byte_size), 0) FROM item_version_byte_sizes WHERE item_version_byte_sizes.tbl_node_fk = fsnode_archive_object_summary.tbl_node_fk ) WHERE EXISTS ( SELECT 1 FROM item_version_byte_sizes WHERE item_version_byte_sizes.tbl_node_fk = fsnode_archive_object_summary.tbl_node_fk ); END IF; END IF; RETURN new; END; $$;


--
-- Name: trigger_fct_tau_link_tn_tiv_fao(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.trigger_fct_tau_link_tn_tiv_fao() RETURNS trigger
    LANGUAGE plpgsql
    AS $$ DECLARE runwayAvail integer; BEGIN SELECT count(*) INTO runwayAvail FROM fsnode_archive_object_summary WHERE tbl_node_fk = new.tbl_node_fk; UPDATE fsnode_archive_object_summary SET bytes_total = bytes_total - tbl_item_version.byte_size, bytes_online = bytes_online - (tbl_item_version.byte_size * CAST(fsnode_archive_object.is_online AS integer)) FROM tbl_item_version, fsnode_archive_object WHERE old.tbl_item_version_fk = tbl_item_version.id AND old.fsnode_archive_object_fk = fsnode_archive_object.id AND old.tbl_node_fk = tbl_node_fk; IF runwayAvail = 0 THEN INSERT INTO fsnode_archive_object_summary (tbl_node_fk, bytes_total, bytes_online) SELECT new.tbl_node_fk , tbl_item_version.byte_size , tbl_item_version.byte_size * CAST(fsnode_archive_object.is_online AS integer) FROM tbl_item_version, fsnode_archive_object WHERE new.tbl_item_version_fk = tbl_item_version.id AND new.fsnode_archive_object_fk = fsnode_archive_object.id; ELSE UPDATE fsnode_archive_object_summary SET bytes_total = bytes_total + tbl_item_version.byte_size, bytes_online = bytes_online + (tbl_item_version.byte_size * CAST(fsnode_archive_object.is_online AS integer)) FROM tbl_item_version, fsnode_archive_object WHERE new.tbl_item_version_fk = tbl_item_version.id AND new.fsnode_archive_object_fk = fsnode_archive_object.id AND new.tbl_node_fk = tbl_node_fk; END IF; RETURN new; END; $$;


--
-- Name: trigger_fct_tau_tbl_item_version(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.trigger_fct_tau_tbl_item_version() RETURNS trigger
    LANGUAGE plpgsql
    AS $$ BEGIN UPDATE fsnode_archive_object_summary SET bytes_total = bytes_total - old.byte_size, bytes_online = bytes_online - (old.byte_size * CAST(fsnode_archive_object.is_online AS integer)) FROM link_tbl_node_tbl_item_version_fsnode_archive_object JOIN fsnode_archive_object ON fsnode_archive_object_fk = fsnode_archive_object.id WHERE old.id = tbl_item_version_fk AND link_tbl_node_tbl_item_version_fsnode_archive_object.tbl_node_fk = fsnode_archive_object_summary.tbl_node_fk; UPDATE fsnode_archive_object_summary SET bytes_total = bytes_total + new.byte_size, bytes_online = bytes_online + (new.byte_size * CAST(fsnode_archive_object.is_online AS integer)) FROM link_tbl_node_tbl_item_version_fsnode_archive_object JOIN fsnode_archive_object ON fsnode_archive_object_fk = fsnode_archive_object.id WHERE old.id = tbl_item_version_fk AND link_tbl_node_tbl_item_version_fsnode_archive_object.tbl_node_fk = fsnode_archive_object_summary.tbl_node_fk; RETURN new; END; $$;


--
-- Name: trigger_fct_tbd_ar_file(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.trigger_fct_tbd_ar_file() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  UPDATE ar_file_summary 
    SET bytes_total = bytes_total-old.file_size,
        bytes_online= bytes_online-(old.file_size * CAST(old.is_online AS integer))
  WHERE runway_name = old.runway_name;
  RETURN old;
END;
     
$$;


--
-- Name: trigger_fct_tbd_fsnode_archive_object(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.trigger_fct_tbd_fsnode_archive_object() RETURNS trigger
    LANGUAGE plpgsql
    AS $$ BEGIN WITH item_version_byte_sizes AS ( SELECT tbl_item_version.byte_size AS byte_size , link_tbl_node_tbl_item_version_fsnode_archive_object.tbl_node_fk AS tbl_node_fk FROM tbl_item_version JOIN link_tbl_node_tbl_item_version_fsnode_archive_object ON tbl_item_version_fk = tbl_item_version.id WHERE old.id = fsnode_archive_object_fk ) UPDATE fsnode_archive_object_summary SET bytes_total = bytes_total - ( SELECT coalesce(sum(item_version_byte_sizes.byte_size), 0) FROM item_version_byte_sizes WHERE item_version_byte_sizes.tbl_node_fk = fsnode_archive_object_summary.tbl_node_fk ) WHERE EXISTS ( SELECT 1 FROM item_version_byte_sizes WHERE item_version_byte_sizes.tbl_node_fk = fsnode_archive_object_summary.tbl_node_fk ); WITH item_version_byte_sizes AS ( SELECT tbl_item_version.byte_size AS byte_size , link_tbl_node_tbl_item_version_fsnode_archive_object.tbl_node_fk AS tbl_node_fk FROM tbl_item_version JOIN link_tbl_node_tbl_item_version_fsnode_archive_object ON tbl_item_version_fk = tbl_item_version.id WHERE old.id = fsnode_archive_object_fk AND old.is_online ) UPDATE fsnode_archive_object_summary SET bytes_online = bytes_online - ( SELECT coalesce(sum(item_version_byte_sizes.byte_size), 0) FROM item_version_byte_sizes WHERE item_version_byte_sizes.tbl_node_fk = fsnode_archive_object_summary.tbl_node_fk ) WHERE EXISTS ( SELECT 1 FROM item_version_byte_sizes WHERE item_version_byte_sizes.tbl_node_fk = fsnode_archive_object_summary.tbl_node_fk ); RETURN old; END; $$;


--
-- Name: trigger_fct_tbd_ldikw_update_ldkwa(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.trigger_fct_tbd_ldikw_update_ldkwa() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  document_id bigint;
BEGIN
  SELECT ds.dicom_study_fk INTO document_id FROM dicom_series ds 
        JOIN dicom_image di ON di.dicom_series_fk=ds.id
        WHERE di.id=old.dicom_image_fk;
  PERFORM synedra_kw_delete(old.keyword_fk, document_id);
  RETURN old;
END
$$;


--
-- Name: trigger_fct_tbd_ldkw_update_ldkwa(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.trigger_fct_tbd_ldkw_update_ldkwa() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  document_id bigint;
BEGIN
  document_id := old.document_fk;
  PERFORM synedra_kw_delete(old.keyword_fk, document_id);
  RETURN old;
END
$$;


--
-- Name: trigger_fct_tbd_ldskw_update_ldkwa(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.trigger_fct_tbd_ldskw_update_ldkwa() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  document_id bigint;
BEGIN
  SELECT ds.dicom_study_fk INTO document_id FROM dicom_series ds WHERE ds.id=old.dicom_series_fk;
  PERFORM synedra_kw_delete(old.keyword_fk, document_id);
  RETURN old;
END
$$;


--
-- Name: trigger_fct_tbd_lgfkw_update_ldkwa(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.trigger_fct_tbd_lgfkw_update_ldkwa() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  document_id bigint;
BEGIN
  SELECT gf.generic_container_fk INTO document_id FROM generic_file gf
        WHERE gf.id=old.generic_file_fk; 
  PERFORM synedra_kw_delete(old.keyword_fk, document_id);
  RETURN old;
END;
$$;


--
-- Name: trigger_fct_tbd_link_tn_tiv_fao(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.trigger_fct_tbd_link_tn_tiv_fao() RETURNS trigger
    LANGUAGE plpgsql
    AS $$ BEGIN UPDATE fsnode_archive_object_summary SET bytes_total = bytes_total - tbl_item_version.byte_size, bytes_online = bytes_online - (tbl_item_version.byte_size * CAST(fsnode_archive_object.is_online AS integer)) FROM tbl_item_version, fsnode_archive_object WHERE old.tbl_item_version_fk = tbl_item_version.id AND old.fsnode_archive_object_fk = fsnode_archive_object.id AND old.tbl_node_fk = tbl_node_fk; RETURN old; END; $$;


--
-- Name: trigger_fct_tbd_tbl_item_version(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.trigger_fct_tbd_tbl_item_version() RETURNS trigger
    LANGUAGE plpgsql
    AS $$ BEGIN UPDATE fsnode_archive_object_summary SET bytes_total = bytes_total - old.byte_size, bytes_online = bytes_online - (old.byte_size * CAST(fsnode_archive_object.is_online AS integer)) FROM link_tbl_node_tbl_item_version_fsnode_archive_object, fsnode_archive_object WHERE tbl_item_version_fk = old.id AND fsnode_archive_object_fk = fsnode_archive_object.id AND fsnode_archive_object_summary.tbl_node_fk = link_tbl_node_tbl_item_version_fsnode_archive_object.tbl_node_fk; RETURN old; END; $$;


--
-- Name: trigger_fct_tbiu_extension_lower_abbr(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.trigger_fct_tbiu_extension_lower_abbr() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  new.abbreviation := lower(new.abbreviation);
  RETURN new;
END;
     
$$;


--
-- Name: trigger_fct_tbiu_generic_file_lower_ext(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.trigger_fct_tbiu_generic_file_lower_ext() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    new.extension := lower(new.extension);
    RETURN new;
END;     
     
$$;


--
-- Name: trigger_fct_tbiu_order_e_upper(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.trigger_fct_tbiu_order_e_upper() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  new.order_status_fk := upper(new.order_status_fk);
  new.modality_fk := upper(new.modality_fk);
  new.accession_number := upper(new.accession_number);
  RETURN NEW;
END
$$;


--
-- Name: trigger_fct_tbiu_order_r_upper(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.trigger_fct_tbiu_order_r_upper() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  new.domain := upper(new.domain);
  RETURN NEW;
END
$$;


--
-- Name: trigger_fct_tbiu_orderer_upper(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.trigger_fct_tbiu_orderer_upper() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  new.orgunit_fk := upper(new.orgunit_fk);
  new.type_fk := upper(new.type_fk);
  new.abbrevation := upper(new.abbrevation);
  RETURN NEW;
END
$$;


--
-- Name: trigger_fct_tbiu_orgunit_upper(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.trigger_fct_tbiu_orgunit_upper() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  new.abk := upper(new.abk);
  RETURN NEW;
END;
$$;


--
-- Name: vip_document_default(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.vip_document_default() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
	IF (new.link_patient_visit_fk IS NOT NULL) THEN
		SELECT COALESCE(visit.vip_indicator_fk, patient.vip_indicator_fk) INTO new.vip_indicator_fk
		FROM link_patient_visit
		JOIN patient ON link_patient_visit.patient_fk = patient.id
		LEFT JOIN visit ON link_patient_visit.visit_fk = visit.id
		WHERE link_patient_visit.id = new.link_patient_visit_fk;
	END IF;
	RETURN new;
END;
$$;


--
-- Name: vip_document_reassign(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.vip_document_reassign() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
	IF (new.link_patient_visit_fk IS NOT NULL AND new.link_patient_visit_fk <> old.link_patient_visit_fk) THEN
		SELECT COALESCE(visit.vip_indicator_fk, patient.vip_indicator_fk) INTO new.vip_indicator_fk
		FROM link_patient_visit
		JOIN patient ON link_patient_visit.patient_fk = patient.id
		LEFT JOIN visit ON link_patient_visit.visit_fk = visit.id
		WHERE link_patient_visit.id = new.link_patient_visit_fk;
	END IF;
	RETURN new;
END;
$$;


--
-- Name: vip_lpvinsert_to_visit(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.vip_lpvinsert_to_visit() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
	IF (new.visit_fk IS NOT NULL) THEN
		UPDATE visit SET vip_indicator_fk = (SELECT vip_indicator_fk FROM patient WHERE id = new.patient_fk)
		WHERE visit.vip_indicator_fk = '- ' AND visit.id = new.visit_fk;
	END IF;
	RETURN new;
END;
$$;


--
-- Name: vip_lpvupdate_to_visit(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.vip_lpvupdate_to_visit() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
  patientVIP char(2);
BEGIN
	IF (new.patient_fk <> old.patient_fk AND new.visit_fk IS NOT NULL) THEN
		SELECT vip_indicator_fk INTO patientVIP FROM patient WHERE id = new.patient_fk;

		IF (patientVIP <> '- ') THEN
			UPDATE visit SET vip_indicator_fk = patientVIP 
			WHERE visit.id = new.visit_fk;
		END IF;
	END IF;
	RETURN new;
END;
$$;


--
-- Name: vip_patient_to_visits_and_documents(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.vip_patient_to_visits_and_documents() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
	IF (new.vip_indicator_fk <> old.vip_indicator_fk) THEN
		UPDATE visit SET vip_indicator_fk = new.vip_indicator_fk
		WHERE visit.id IN (SELECT visit_fk FROM link_patient_visit WHERE patient_fk = new.id)
		AND visit.vip_indicator_fk <> new.vip_indicator_fk;

		UPDATE document SET vip_indicator_fk = new.vip_indicator_fk
		WHERE document.link_patient_visit_fk IN (SELECT id FROM link_patient_visit WHERE patient_fk = new.id AND visit_fk IS NULL);
	END IF;
	RETURN new;
END;
$$;


--
-- Name: vip_visit_to_documents(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.vip_visit_to_documents() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
	IF (new.vip_indicator_fk <> old.vip_indicator_fk) THEN
		UPDATE document SET vip_indicator_fk = new.vip_indicator_fk
		WHERE document.link_patient_visit_fk IN (SELECT id FROM link_patient_visit WHERE visit_fk = new.id);
	END IF;
	RETURN new;
END;
$$;


-- SET default_tablespace = syn_tbl;

SET default_with_oids = false;

--
-- Name: aet; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.aet (
    id bigint NOT NULL,
    name character varying(255) NOT NULL,
    description character varying(255),
    orgunit_fk bigint,
    modality_fk character varying(20),
    pacs_user_fk bigint,
    receiving_host_fk bigint,
    receiving_port integer,
    receiving_sync_sc boolean DEFAULT true,
    sender_title character varying(255),
    max_associations smallint DEFAULT 1 NOT NULL,
    servertag character varying(64) DEFAULT ''::character varying NOT NULL,
    characterset_fk character varying(64) DEFAULT 'ISO_IR 192'::character varying NOT NULL,
    request_storage_commitment boolean DEFAULT false NOT NULL,
    send_encrypted boolean DEFAULT false NOT NULL,
    CONSTRAINT aet_max_associations_positive CHECK ((max_associations > 0)),
    CONSTRAINT aet_name_nn CHECK ((name IS NOT NULL)),
    CONSTRAINT aet_rss_nn CHECK ((receiving_sync_sc IS NOT NULL))
);


--
-- Name: TABLE aet; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.aet IS 'ORG:10,10';


--
-- Name: aet_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.aet_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 20;


--
-- Name: aim_conversion_interface; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.aim_conversion_interface (
    id bigint NOT NULL,
    identifier character varying(50) NOT NULL,
    subsystem character varying(50) NOT NULL,
    disabled boolean NOT NULL,
    key character varying(250) NOT NULL,
    value character varying(250),
    is_regex boolean NOT NULL,
    order_regex smallint DEFAULT 0,
    direction character varying(3) DEFAULT 'IN'::character varying,
    description character varying(250),
    CONSTRAINT aimconint_dir_chk CHECK (((direction)::text = ANY (ARRAY[('IN'::character varying)::text, ('OUT'::character varying)::text]))),
    CONSTRAINT aimconint_dir_nn CHECK ((direction IS NOT NULL)),
    CONSTRAINT aimconint_dis_nn CHECK ((disabled IS NOT NULL)),
    CONSTRAINT aimconint_ide_nn CHECK ((identifier IS NOT NULL)),
    CONSTRAINT aimconint_isreg_nn CHECK ((is_regex IS NOT NULL)),
    CONSTRAINT aimconint_key_nn CHECK ((key IS NOT NULL)),
    CONSTRAINT aimconint_ordreg_nn CHECK ((order_regex IS NOT NULL)),
    CONSTRAINT aimconint_sub_nn CHECK ((subsystem IS NOT NULL))
);


--
-- Name: TABLE aim_conversion_interface; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.aim_conversion_interface IS 'BACKEND:30,0';


--
-- Name: aim_conversion_interface_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.aim_conversion_interface_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 20;


--
-- Name: aim_db_version; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.aim_db_version (
    version smallint NOT NULL,
    major smallint NOT NULL,
    minor smallint NOT NULL,
    release smallint,
    last_successful_backup timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
    serial_number integer DEFAULT 1,
    audit_enterprise_site_id character varying(256),
    last_successful_metacore timestamp without time zone,
    CONSTRAINT aim_db_vers_minor_major_nn CHECK ((major IS NOT NULL)),
    CONSTRAINT aim_db_vers_minor_nn CHECK ((minor IS NOT NULL)),
    CONSTRAINT aim_db_vers_minor_version_nn CHECK ((version IS NOT NULL))
);


--
-- Name: TABLE aim_db_version; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.aim_db_version IS 'BACKEND:70,0';


--
-- Name: annotation; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.annotation (
    id bigint NOT NULL,
    dicom_image_fk bigint,
    generic_file_fk bigint,
    value text NOT NULL,
    document_fk bigint,
    CONSTRAINT annotation_item_references_exactly_one CHECK ((((document_fk IS NOT NULL) AND (dicom_image_fk IS NULL) AND (generic_file_fk IS NULL)) OR ((document_fk IS NULL) AND (dicom_image_fk IS NOT NULL) AND (generic_file_fk IS NULL)) OR ((document_fk IS NULL) AND (dicom_image_fk IS NULL) AND (generic_file_fk IS NOT NULL))))
);


--
-- Name: TABLE annotation; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.annotation IS 'KEYWORD:10,25';


--
-- Name: annotation_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.annotation_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: ar_archive_object; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.ar_archive_object (
    id bigint NOT NULL,
    identifier character varying(4000),
    status character varying(100) NOT NULL,
    checksum character varying(2000) NOT NULL,
    checksum_format character varying(100) NOT NULL,
    archive_format character varying(100) NOT NULL,
    archive_format_options character varying(2000) NOT NULL,
    cipher character varying(4000) NOT NULL,
    cipher_format character varying(100) NOT NULL,
    cipher_format_options character varying(4000) NOT NULL,
    created_when timestamp without time zone DEFAULT now(),
    archived_when timestamp without time zone,
    completed_when timestamp without time zone,
    verified_when timestamp without time zone,
    runway_name character varying(100) NOT NULL,
    migrated boolean,
    CONSTRAINT ar_archive_object_runame_nn CHECK ((runway_name IS NOT NULL)),
    CONSTRAINT ararchiveobject_archformopt_nn CHECK ((archive_format_options IS NOT NULL)),
    CONSTRAINT ararchiveobject_archiveform_nn CHECK ((archive_format IS NOT NULL)),
    CONSTRAINT ararchiveobject_checksum_nn CHECK ((checksum IS NOT NULL)),
    CONSTRAINT ararchiveobject_chksumform_nn CHECK ((checksum_format IS NOT NULL)),
    CONSTRAINT ararchiveobject_cipher_nn CHECK ((cipher IS NOT NULL)),
    CONSTRAINT ararchiveobject_cipherform_nn CHECK ((cipher_format IS NOT NULL)),
    CONSTRAINT ararchiveobject_ciphformopt_nn CHECK ((cipher_format_options IS NOT NULL)),
    CONSTRAINT ararchiveobject_createdwhen_nn CHECK ((created_when IS NOT NULL)),
    CONSTRAINT ararchiveobject_status_nn CHECK ((status IS NOT NULL))
)
WITH (autovacuum_vacuum_scale_factor='0.05', autovacuum_vacuum_threshold='10000');


--
-- Name: TABLE ar_archive_object; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.ar_archive_object IS 'ARCHIVE:15,12';


--
-- Name: ar_archive_object_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.ar_archive_object_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 20;


--
-- Name: ar_file; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.ar_file (
    id bigint NOT NULL,
    path character varying(2000),
    name character varying(2000) NOT NULL,
    md5 character varying(100) NOT NULL,
    crc32 character varying(100) NOT NULL,
    file_size bigint NOT NULL,
    is_online boolean NOT NULL,
    status character varying(100) NOT NULL,
    injected_when timestamp without time zone NOT NULL,
    last_access_when timestamp without time zone NOT NULL,
    runway_name character varying(100) NOT NULL,
    CONSTRAINT ar_file_runame_nn CHECK ((runway_name IS NOT NULL)),
    CONSTRAINT arfile_crc32_nn CHECK ((crc32 IS NOT NULL)),
    CONSTRAINT arfile_filesize_nn CHECK ((file_size IS NOT NULL)),
    CONSTRAINT arfile_injectedwhen_nn CHECK ((injected_when IS NOT NULL)),
    CONSTRAINT arfile_isonline_nn CHECK ((is_online IS NOT NULL)),
    CONSTRAINT arfile_lastaccesswhen_nn CHECK ((last_access_when IS NOT NULL)),
    CONSTRAINT arfile_md5_nn CHECK ((md5 IS NOT NULL)),
    CONSTRAINT arfile_name_nn CHECK ((name IS NOT NULL)),
    CONSTRAINT arfile_status_nn CHECK ((status IS NOT NULL))
)
WITH (autovacuum_vacuum_scale_factor='0.005', autovacuum_vacuum_threshold='100000');
ALTER TABLE ONLY public.ar_file ALTER COLUMN path SET STATISTICS 10000;


--
-- Name: TABLE ar_file; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.ar_file IS 'ARCHIVE:10,11';


--
-- Name: ar_file_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.ar_file_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 20;


--
-- Name: ar_file_summary; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.ar_file_summary (
    runway_name character varying(100),
    bytes_total bigint NOT NULL,
    bytes_online bigint NOT NULL,
    CONSTRAINT ars_bytes_online CHECK ((bytes_online IS NOT NULL)),
    CONSTRAINT ars_bytes_total CHECK ((bytes_total IS NOT NULL))
);


--
-- Name: TABLE ar_file_summary; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.ar_file_summary IS 'ARCHIVE:15,10';


--
-- Name: ar_link_archive_object_file; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.ar_link_archive_object_file (
    ar_archive_object_fk bigint NOT NULL,
    ar_file_fk bigint NOT NULL,
    CONSTRAINT arlink_archobject_nn CHECK ((ar_archive_object_fk IS NOT NULL)),
    CONSTRAINT arlink_arfile_nn CHECK ((ar_file_fk IS NOT NULL))
);
ALTER TABLE ONLY public.ar_link_archive_object_file ALTER COLUMN ar_archive_object_fk SET STATISTICS 10000;


--
-- Name: TABLE ar_link_archive_object_file; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.ar_link_archive_object_file IS 'ARCHIVE:10,15';


--
-- Name: audit_event; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.audit_event (
    id bigint NOT NULL,
    "time" timestamp without time zone DEFAULT now(),
    type character varying(255) NOT NULL,
    migrated boolean,
    CONSTRAINT audit_event_time_nn CHECK (("time" IS NOT NULL)),
    CONSTRAINT audit_event_type_nn CHECK ((type IS NOT NULL))
);


--
-- Name: TABLE audit_event; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.audit_event IS 'BACKEND:0,10';


--
-- Name: audit_event_property; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.audit_event_property (
    id bigint NOT NULL,
    audit_event_fk bigint NOT NULL,
    key_fk smallint NOT NULL,
    value character varying(255),
    CONSTRAINT aep_audit_event_fk_nn CHECK ((audit_event_fk IS NOT NULL))
);
ALTER TABLE ONLY public.audit_event_property ALTER COLUMN audit_event_fk SET STATISTICS 10000;


--
-- Name: TABLE audit_event_property; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.audit_event_property IS 'BACKEND:10,10';


--
-- Name: audit_event_property_key; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.audit_event_property_key (
    id smallint NOT NULL,
    key character varying(255) NOT NULL
);


--
-- Name: TABLE audit_event_property_key; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.audit_event_property_key IS 'BACKEND:20,10';


--
-- Name: audit_event_property_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.audit_event_property_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 20;


--
-- Name: audit_event_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.audit_event_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 20;


--
-- Name: audit_record; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.audit_record (
    id bigint NOT NULL,
    audit_source_id character varying(1024) NOT NULL,
    event_date_time timestamp with time zone NOT NULL,
    event_action_code character(1) NOT NULL,
    event_outcome_indicator smallint NOT NULL,
    event_id_code character varying(256) NOT NULL,
    event_type_code character varying(256),
    event_type_code_system_name character varying(256),
    event_type_code_original_text character varying(1024),
    user_id character varying(1024),
    user_alt_id character varying(1024),
    user_name character varying(1024),
    source_id character varying(1024),
    source_alt_id character varying(1024),
    source_network_access_point_id character varying(1024),
    destination_id character varying(1024),
    destination_alt_id character varying(1024),
    destination_network_access_point_id character varying(1024),
    patient_id character varying(1024),
    inserted_when timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
    affected_pacs_user_fk bigint,
    dicom_image_fk bigint,
    dicom_series_fk bigint,
    document_fk bigint,
    generic_file_fk bigint,
    orgunit_fk bigint,
    pacs_user_fk bigint,
    patient_fk bigint,
    target_dicom_series_fk bigint,
    target_document_fk bigint,
    target_patient_fk bigint,
    target_visit_fk bigint,
    visit_fk bigint,
    role_fk character varying(255),
    media character varying(256),
    archived boolean DEFAULT false NOT NULL,
    o_procedure_fk bigint
);


--
-- Name: TABLE audit_record; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.audit_record IS 'BACKEND:63,0';


--
-- Name: audit_record_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.audit_record_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: audit_record_source; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.audit_record_source (
    audit_record_fk bigint NOT NULL,
    source xml NOT NULL
);


--
-- Name: TABLE audit_record_source; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.audit_record_source IS 'BACKEND:63,10';


--
-- Name: catalog_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.catalog_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 20;


--
-- Name: catkey_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.catkey_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 20;


--
-- Name: catval_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.catval_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 20;


--
-- Name: characterset; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.characterset (
    name character varying(64) NOT NULL,
    description character varying(256) NOT NULL
);


--
-- Name: TABLE characterset; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.characterset IS 'ORG:8,11';


--
-- Name: config_entry_base; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.config_entry_base (
    id bigint NOT NULL,
    config_entry_description_fk bigint NOT NULL,
    product_fk bigint NOT NULL,
    value text,
    CONSTRAINT cfg_entr_bas_cfg_entry_desc_nn CHECK ((config_entry_description_fk IS NOT NULL)),
    CONSTRAINT cfg_entr_bas_product_fk_nn CHECK ((product_fk IS NOT NULL))
);


--
-- Name: TABLE config_entry_base; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.config_entry_base IS 'CONFIG:10,20';


--
-- Name: config_entry_base_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.config_entry_base_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 20;


--
-- Name: config_entry_description; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.config_entry_description (
    id bigint NOT NULL,
    config_section_description_fk bigint NOT NULL,
    name character varying(256) NOT NULL,
    description character varying(1024),
    CONSTRAINT cfg_entry_desc_name_nn CHECK ((name IS NOT NULL)),
    CONSTRAINT cfg_entry_desc_sec_nn CHECK ((config_section_description_fk IS NOT NULL))
);


--
-- Name: TABLE config_entry_description; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.config_entry_description IS 'CONFIG:10,10';


--
-- Name: config_entry_description_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.config_entry_description_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 20;


--
-- Name: config_section_description; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.config_section_description (
    id bigint NOT NULL,
    name character varying(256) NOT NULL,
    description character varying(1024),
    CONSTRAINT cfg_section_desc_name_nn CHECK ((name IS NOT NULL))
);


--
-- Name: TABLE config_section_description; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.config_section_description IS 'CONFIG:10,0';


--
-- Name: config_section_description_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.config_section_description_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 20;


--
-- Name: container_job_result_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.container_job_result_seq
    START WITH 100
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 10;


--
-- Name: container_job_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.container_job_seq
    START WITH 100
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: default_config; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.default_config (
    config_entry_base_fk bigint NOT NULL
);


--
-- Name: TABLE default_config; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.default_config IS 'CONFIG:0,0';


--
-- Name: diagnostic_report; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.diagnostic_report (
    id bigint NOT NULL,
    fhir jsonb NOT NULL,
    generic_file_fk bigint,
    effective_date_time timestamp with time zone,
    effective_period tstzrange,
    fhir_version smallint
);


--
-- Name: TABLE diagnostic_report; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.diagnostic_report IS 'FHIR:20,20';


--
-- Name: diagnostic_report_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.diagnostic_report_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: dicom_image; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.dicom_image (
    id bigint NOT NULL,
    dicom_series_fk bigint NOT NULL,
    sop_instance_uid character varying(64) NOT NULL,
    archive_item_name character varying(256),
    inserted_when timestamp without time zone NOT NULL,
    inserted_by_fk smallint NOT NULL,
    last_modified_when timestamp without time zone NOT NULL,
    last_modified_by_fk smallint NOT NULL,
    deleted_when timestamp without time zone,
    deleted_by_fk smallint,
    status_deleted smallint NOT NULL,
    imagetype character varying(200),
    photometricinterpretation character varying(50),
    image_rows integer,
    image_columns integer,
    samplesperpixel integer,
    bitsallocated integer,
    bitsstored integer,
    transfersyntaxuid character varying(100),
    sopclassuid character varying(100),
    imagecomments character varying(1000),
    acquisitionnumber character varying(50),
    instancenumber integer,
    number_of_frames integer,
    CONSTRAINT dicimg_dicserfk_nn CHECK ((dicom_series_fk IS NOT NULL)),
    CONSTRAINT dicimg_id_nn CHECK ((id IS NOT NULL)),
    CONSTRAINT dicimg_ins_by_fk_nn CHECK ((inserted_by_fk IS NOT NULL)),
    CONSTRAINT dicimg_ins_when_fk_nn CHECK ((inserted_when IS NOT NULL)),
    CONSTRAINT dicimg_last_mod_by_fk_nn CHECK ((last_modified_by_fk IS NOT NULL)),
    CONSTRAINT dicimg_last_mod_when_fk_nn CHECK ((last_modified_when IS NOT NULL)),
    CONSTRAINT dicimg_sop_in_uid_nn CHECK ((sop_instance_uid IS NOT NULL)),
    CONSTRAINT dicimg_status_deleted_nn CHECK ((status_deleted IS NOT NULL))
);
ALTER TABLE ONLY public.dicom_image ALTER COLUMN dicom_series_fk SET STATISTICS 10000;


--
-- Name: TABLE dicom_image; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.dicom_image IS 'DATA:36,30';


--
-- Name: dicom_image_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.dicom_image_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: dicom_mail_recipient; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.dicom_mail_recipient (
    id bigint NOT NULL,
    address character varying(255) NOT NULL,
    is_internal boolean NOT NULL,
    is_receiver boolean NOT NULL,
    pgp_public_key character varying(8000) NOT NULL,
    pgp_secret_key character varying(8000),
    passphrase character varying(255),
    mail_server_type character varying(20),
    mail_server_host character varying(255),
    mail_server_port integer,
    mail_server_username character varying(255),
    mail_server_password character varying(255),
    orgunit_fk bigint,
    CONSTRAINT dicom_mail_recipient_is_internal_is_receiver_consistence CHECK (((NOT is_receiver) OR is_internal)),
    CONSTRAINT dicom_mail_recipient_is_internal_sanity CHECK (((NOT is_internal) OR ((NOT (pgp_secret_key IS NULL)) AND (NOT (passphrase IS NULL))))),
    CONSTRAINT dicom_mail_recipient_is_receiver_sanity CHECK (((NOT is_receiver) OR ((NOT (mail_server_type IS NULL)) AND (NOT (mail_server_host IS NULL)) AND (NOT (mail_server_port IS NULL)) AND (NOT (mail_server_username IS NULL)) AND (NOT (mail_server_password IS NULL)) AND (NOT (orgunit_fk IS NULL)))))
);


--
-- Name: TABLE dicom_mail_recipient; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.dicom_mail_recipient IS 'BACKEND:65,0';


--
-- Name: dicom_mail_recipient_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.dicom_mail_recipient_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: dicom_series; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.dicom_series (
    id bigint NOT NULL,
    dicom_study_fk bigint,
    modality_fk character varying(20),
    archive_container_name character varying(256),
    series_instance_uid character varying(250) NOT NULL,
    num_images integer NOT NULL,
    series_description character varying(200),
    inserted_when timestamp without time zone NOT NULL,
    inserted_by_fk smallint NOT NULL,
    last_modified_when timestamp without time zone NOT NULL,
    last_modified_by_fk smallint NOT NULL,
    deleted_when timestamp without time zone,
    deleted_by_fk smallint,
    status_deleted smallint NOT NULL,
    manufacturer character varying(100),
    manufacturersmodelname character varying(100),
    institutionname character varying(100),
    stationname character varying(50),
    referringphysiciansname character varying(350),
    operatorsname character varying(350),
    performingphysiciansname character varying(350),
    physicianofrecord character varying(350),
    seriesnumber integer,
    step_id character varying(16),
    CONSTRAINT dicser_dicstudfk_nn CHECK ((dicom_study_fk IS NOT NULL)),
    CONSTRAINT dicser_ins_by_fk_nn CHECK ((inserted_by_fk IS NOT NULL)),
    CONSTRAINT dicser_ins_when_fk_nn CHECK ((inserted_when IS NOT NULL)),
    CONSTRAINT dicser_last_mod_by_fk_nn CHECK ((last_modified_by_fk IS NOT NULL)),
    CONSTRAINT dicser_last_mod_when_fk_nn CHECK ((last_modified_when IS NOT NULL)),
    CONSTRAINT dicser_modal_nn CHECK ((modality_fk IS NOT NULL)),
    CONSTRAINT dicser_num_images_nn CHECK ((num_images IS NOT NULL)),
    CONSTRAINT dicser_ser_in_uid_nn CHECK ((series_instance_uid IS NOT NULL)),
    CONSTRAINT dicser_status_deleted_nn CHECK ((status_deleted IS NOT NULL))
);


--
-- Name: TABLE dicom_series; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.dicom_series IS 'DATA:40,40';


--
-- Name: dicom_series_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.dicom_series_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: dicom_study; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.dicom_study (
    document_fk bigint NOT NULL,
    study_instance_uid character varying(250) NOT NULL,
    calling_aet character varying(50),
    called_aet character varying(50),
    calling_host character varying(50),
    num_series smallint NOT NULL,
    num_images integer NOT NULL,
    inserted_when timestamp without time zone NOT NULL,
    inserted_by_fk smallint NOT NULL,
    last_modified_when timestamp without time zone NOT NULL,
    last_modified_by_fk smallint NOT NULL,
    deleted_when timestamp without time zone,
    deleted_by_fk smallint,
    status_deleted smallint NOT NULL,
    all_modalities character varying(512),
    accessionnumber character varying(50),
    institutionaldepartmentname character varying(100),
    nameofphysiciansreadingstudy character varying(350),
    body_part_examined character varying(50),
    studyid character varying(50),
    CONSTRAINT dicstud_ins_by_fk_nn CHECK ((inserted_by_fk IS NOT NULL)),
    CONSTRAINT dicstud_ins_when_fk_nn CHECK ((inserted_when IS NOT NULL)),
    CONSTRAINT dicstud_last_mod_by_fk_nn CHECK ((last_modified_by_fk IS NOT NULL)),
    CONSTRAINT dicstud_last_mod_when_fk_nn CHECK ((last_modified_when IS NOT NULL)),
    CONSTRAINT dicstud_num_images_nn CHECK ((num_images IS NOT NULL)),
    CONSTRAINT dicstud_num_series_nn CHECK ((num_series IS NOT NULL)),
    CONSTRAINT dicstud_status_deleted_nn CHECK ((status_deleted IS NOT NULL)),
    CONSTRAINT dicstud_stud_in_uid_nn CHECK ((study_instance_uid IS NOT NULL))
);


--
-- Name: TABLE dicom_study; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.dicom_study IS 'DATA:40,50';


--
-- Name: discharge_disposition; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.discharge_disposition (
    id character(3) NOT NULL,
    description character varying(250)
);


--
-- Name: TABLE discharge_disposition; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.discharge_disposition IS 'DATA:65,70';


--
-- Name: document; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.document (
    id bigint NOT NULL,
    link_patient_visit_fk bigint,
    document_type_fk character varying(30),
    producer bigint,
    orderer bigint,
    description character varying(200) DEFAULT ''::character varying,
    instance_uid character varying(256) DEFAULT NULL::character varying NOT NULL,
    document_created_when timestamp without time zone,
    inserted_when timestamp without time zone NOT NULL,
    inserted_by_fk smallint NOT NULL,
    last_modified_when timestamp without time zone NOT NULL,
    last_modified_by_fk smallint NOT NULL,
    deleted_when timestamp without time zone,
    deleted_by_fk smallint,
    status_deleted smallint NOT NULL,
    record_type_fk smallint,
    record_subtype_fk smallint,
    index01 character varying(1000),
    index02 character varying(1000),
    index03 character varying(1000),
    index04 character varying(1000),
    index05 character varying(1000),
    index06 character varying(1000),
    index07 character varying(1000),
    index08 character varying(1000),
    index09 character varying(1000),
    index10 character varying(1000),
    index11 character varying(1000),
    index12 character varying(1000),
    index13 character varying(1000),
    index14 character varying(1000),
    index15 character varying(1000),
    subindex01 character varying(1000),
    subindex02 character varying(1000),
    subindex03 character varying(1000),
    subindex04 character varying(1000),
    subindex05 character varying(1000),
    subindex06 character varying(1000),
    subindex07 character varying(1000),
    subindex08 character varying(1000),
    subindex09 character varying(1000),
    subindex10 character varying(1000),
    subindex11 character varying(1000),
    subindex12 character varying(1000),
    subindex13 character varying(1000),
    subindex14 character varying(1000),
    subindex15 character varying(1000),
    vip_indicator_fk character(2) DEFAULT '-'::bpchar,
    procedure_id character varying(16),
    document_class_fk bigint,
    CONSTRAINT doc_ins_by_fk_nn CHECK ((inserted_by_fk IS NOT NULL)),
    CONSTRAINT doc_ins_when_fk_nn CHECK ((inserted_when IS NOT NULL)),
    CONSTRAINT doc_instance_uid_nn CHECK ((instance_uid IS NOT NULL)),
    CONSTRAINT doc_last_mod_by_fk_nn CHECK ((last_modified_by_fk IS NOT NULL)),
    CONSTRAINT doc_last_mod_when_fk_nn CHECK ((last_modified_when IS NOT NULL)),
    CONSTRAINT doc_procedure_id_not_empty CHECK (((procedure_id)::text <> ''::text)),
    CONSTRAINT doc_producer_nn CHECK ((producer IS NOT NULL)),
    CONSTRAINT doc_status_deleted_nn CHECK ((status_deleted IS NOT NULL)),
    CONSTRAINT doc_vip_ind_nn CHECK ((vip_indicator_fk IS NOT NULL)),
    CONSTRAINT document_description_nn CHECK ((description IS NOT NULL))
);
ALTER TABLE ONLY public.document ALTER COLUMN description SET STATISTICS 10000;


--
-- Name: TABLE document; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.document IS 'DATA:50,50';


--
-- Name: document_class; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.document_class (
    id bigint NOT NULL,
    coding_scheme character varying(1024) DEFAULT ''::character varying NOT NULL,
    code_value character varying(1024) NOT NULL
);


--
-- Name: TABLE document_class; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.document_class IS 'DATA:20,80';


--
-- Name: document_class_display; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.document_class_display (
    id bigint NOT NULL,
    document_class_fk bigint NOT NULL,
    language_fk character varying(6) NOT NULL,
    display_name character varying(2048) NOT NULL,
    CONSTRAINT document_class_display_name_not_empty CHECK (((display_name)::text <> ''::text))
);


--
-- Name: TABLE document_class_display; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.document_class_display IS 'DATA:20,70';


--
-- Name: document_class_display_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.document_class_display_seq
    START WITH 100
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: document_class_group; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.document_class_group (
    id bigint NOT NULL,
    name character varying(1024) NOT NULL
);


--
-- Name: TABLE document_class_group; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.document_class_group IS 'DATA:40,80';


--
-- Name: document_class_group_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.document_class_group_seq
    START WITH 100
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: document_class_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.document_class_seq
    START WITH 100
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: document_export; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.document_export (
    id bigint NOT NULL,
    document_fk bigint,
    unique_document_fk bigint,
    status character varying(100),
    started_when timestamp without time zone,
    status_changed timestamp without time zone,
    is_exported boolean DEFAULT false
);


--
-- Name: TABLE document_export; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.document_export IS 'DATA:60,65';


--
-- Name: document_export_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.document_export_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: document_import_helper; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.document_import_helper (
    document_fk bigint NOT NULL,
    original_patient_data character varying(256) NOT NULL
);


--
-- Name: TABLE document_import_helper; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.document_import_helper IS 'DATA:60,57';


--
-- Name: document_lock_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.document_lock_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 20;


--
-- Name: document_marker; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.document_marker (
    id bigint NOT NULL,
    value character varying(1000) NOT NULL
);


--
-- Name: TABLE document_marker; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.document_marker IS 'KEYWORD:20,8';


--
-- Name: document_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.document_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: document_share_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.document_share_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: document_share; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.document_share (
    orgunit_fk bigint,
    document_fk bigint,
    valid_until timestamp without time zone,
    done boolean DEFAULT false,
    inserted_when timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone,
    id bigint DEFAULT nextval('public.document_share_seq'::regclass) NOT NULL
);


--
-- Name: TABLE document_share; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.document_share IS 'DATA:60,60';


--
-- Name: document_type; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.document_type (
    id character varying(30) NOT NULL,
    name character varying(30),
    description character varying(200),
    abbr character varying(8)
);


--
-- Name: TABLE document_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.document_type IS 'DATA:60,55';


--
-- Name: extension; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.extension (
    abbreviation character varying(50) NOT NULL,
    description character varying(1000),
    CONSTRAINT extension_abbrev_lower_cc CHECK (((abbreviation)::text = lower((abbreviation)::text)))
);


--
-- Name: TABLE extension; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.extension IS 'BACKEND:30,10';


--
-- Name: extension_group; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.extension_group (
    name character varying(100) NOT NULL,
    description character varying(1000),
    weight bigint
);


--
-- Name: TABLE extension_group; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.extension_group IS 'BACKEND:50,10';


--
-- Name: fhir_identifier; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.fhir_identifier (
    id bigint NOT NULL,
    system text NOT NULL,
    value text NOT NULL
);


--
-- Name: TABLE fhir_identifier; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.fhir_identifier IS 'FHIR:0,10';


--
-- Name: fhir_identifier_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.fhir_identifier_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: fsnode_archive_object; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.fsnode_archive_object (
    id bigint NOT NULL,
    external_identifier text,
    is_online boolean DEFAULT true NOT NULL,
    is_archived boolean DEFAULT false NOT NULL,
    is_ok boolean DEFAULT true NOT NULL,
    md5_checksum character varying(32),
    archive_format public.fsnode_archive_format,
    encryption_format public.fsnode_encryption,
    encryption_key text,
    created_when timestamp without time zone DEFAULT LOCALTIMESTAMP,
    last_access_when timestamp without time zone,
    is_verified boolean DEFAULT false NOT NULL,
    archived_when timestamp without time zone,
    verified_when timestamp without time zone,
    next_retry_when timestamp without time zone,
    num_previous_retries smallint
);


--
-- Name: TABLE fsnode_archive_object; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.fsnode_archive_object IS 'ARCHIVE:20,3';


--
-- Name: fsnode_archive_object_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.fsnode_archive_object_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: fsnode_archive_object_summary; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.fsnode_archive_object_summary (
    tbl_node_fk bigint NOT NULL,
    bytes_total bigint NOT NULL,
    bytes_online bigint NOT NULL
);


--
-- Name: TABLE fsnode_archive_object_summary; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.fsnode_archive_object_summary IS 'ARCHIVE:20,6';


--
-- Name: fsnode_injected_file; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.fsnode_injected_file (
    id bigint NOT NULL,
    tbl_node_fk bigint NOT NULL,
    injected_when timestamp without time zone DEFAULT LOCALTIMESTAMP NOT NULL,
    container_id bigint NOT NULL,
    name text NOT NULL
);


--
-- Name: TABLE fsnode_injected_file; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.fsnode_injected_file IS 'ARCHIVE:20,8';


--
-- Name: fsnode_injected_file_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.fsnode_injected_file_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: generic_container; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.generic_container (
    document_fk bigint NOT NULL,
    archive_container_name character varying(256) NOT NULL,
    name character varying(256) NOT NULL,
    generic_container_uid character varying(256) NOT NULL,
    num_files bigint NOT NULL,
    status_deleted smallint NOT NULL,
    inserted_when timestamp without time zone NOT NULL,
    inserted_by_fk smallint NOT NULL,
    last_modified_when timestamp without time zone NOT NULL,
    last_modified_by_fk smallint NOT NULL,
    deleted_when timestamp without time zone,
    deleted_by_fk smallint,
    all_extensions character varying(500),
    CONSTRAINT gencont_arch_cont_name_nn CHECK ((archive_container_name IS NOT NULL)),
    CONSTRAINT gencont_doc_fk_nn CHECK ((document_fk IS NOT NULL)),
    CONSTRAINT gencont_gencont_uid_nn CHECK ((generic_container_uid IS NOT NULL)),
    CONSTRAINT gencont_ins_by_fk_nn CHECK ((inserted_by_fk IS NOT NULL)),
    CONSTRAINT gencont_ins_when_fk_nn CHECK ((inserted_when IS NOT NULL)),
    CONSTRAINT gencont_last_mod_by_fk_nn CHECK ((last_modified_by_fk IS NOT NULL)),
    CONSTRAINT gencont_last_mod_when_fk_nn CHECK ((last_modified_when IS NOT NULL)),
    CONSTRAINT gencont_name_nn CHECK ((name IS NOT NULL)),
    CONSTRAINT gencont_num_files_nn CHECK ((num_files IS NOT NULL)),
    CONSTRAINT gencont_status_deleted_nn CHECK ((status_deleted IS NOT NULL))
);


--
-- Name: TABLE generic_container; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.generic_container IS 'DATA:60,50';


--
-- Name: generic_file; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.generic_file (
    id bigint NOT NULL,
    generic_container_fk bigint,
    archive_item_name character varying(256) NOT NULL,
    generic_file_uid character varying(256) NOT NULL,
    description character varying(4000),
    extension character varying(50),
    status_deleted smallint NOT NULL,
    inserted_when timestamp without time zone NOT NULL,
    inserted_by_fk smallint NOT NULL,
    last_modified_when timestamp without time zone NOT NULL,
    last_modified_by_fk smallint NOT NULL,
    deleted_when timestamp without time zone,
    deleted_by_fk smallint,
    original_name character varying(256) NOT NULL,
    instancenumber integer DEFAULT 0,
    CONSTRAINT generic_file_ext_lower_cc CHECK (((extension)::text = lower((extension)::text))),
    CONSTRAINT genfile_arch_item_name_nn CHECK ((archive_item_name IS NOT NULL)),
    CONSTRAINT genfile_genfile_uid_nn CHECK ((generic_file_uid IS NOT NULL)),
    CONSTRAINT genfile_id_nn CHECK ((id IS NOT NULL)),
    CONSTRAINT genfile_in_nn CHECK ((instancenumber IS NOT NULL)),
    CONSTRAINT genfile_ins_by_fk_nn CHECK ((inserted_by_fk IS NOT NULL)),
    CONSTRAINT genfile_ins_when_fk_nn CHECK ((inserted_when IS NOT NULL)),
    CONSTRAINT genfile_last_mod_by_fk_nn CHECK ((last_modified_by_fk IS NOT NULL)),
    CONSTRAINT genfile_last_mod_when_fk_nn CHECK ((last_modified_when IS NOT NULL)),
    CONSTRAINT genfile_on_nn CHECK ((original_name IS NOT NULL)),
    CONSTRAINT genfile_status_deleted_nn CHECK ((status_deleted IS NOT NULL))
);


--
-- Name: TABLE generic_file; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.generic_file IS 'DATA:60,30';


--
-- Name: generic_file_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.generic_file_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: hl7_notification; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.hl7_notification (
    id bigint NOT NULL,
    hl7_notification_status_fk smallint,
    document_fk bigint,
    notification_msg text,
    cancellation_msg text,
    error_msg character varying(2000),
    created_when timestamp without time zone,
    last_status_change_when timestamp without time zone,
    notification_sent_when timestamp without time zone,
    cancellation_sent_when timestamp without time zone,
    notification_rsp character varying(4000),
    cancellation_rsp character varying(4000),
    notification_name character varying(255),
    checksum character varying(64),
    binary_content_field character varying(16),
    binary_content_segment_index integer,
    binary_content_repetition_index integer,
    generic_file_fk bigint,
    explicitly_triggered boolean NOT NULL,
    explicit_request_id bigint,
    CONSTRAINT hl7_notif_id_nn CHECK ((id IS NOT NULL))
);


--
-- Name: TABLE hl7_notification; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.hl7_notification IS 'BACKEND:70,10';


--
-- Name: hl7_notification_explicit_request_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.hl7_notification_explicit_request_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: hl7_notification_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.hl7_notification_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: hl7_notification_status; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.hl7_notification_status (
    id smallint NOT NULL,
    key character(2),
    description character varying(512),
    CONSTRAINT hl7notifstat_id_nn CHECK ((id IS NOT NULL))
);


--
-- Name: TABLE hl7_notification_status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.hl7_notification_status IS 'BACKEND:71,10';


--
-- Name: hl7_notification_status_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.hl7_notification_status_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 20;


--
-- Name: hl7proxy_message_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.hl7proxy_message_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 20;


--
-- Name: host; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.host (
    id bigint NOT NULL,
    name character varying(255) NOT NULL,
    ip character varying(15) NOT NULL,
    orgunit_fk bigint,
    CONSTRAINT host_ip_nn CHECK ((ip IS NOT NULL)),
    CONSTRAINT host_name_lc CHECK (((name)::text = lower((name)::text))),
    CONSTRAINT host_name_nn CHECK ((name IS NOT NULL))
);


--
-- Name: TABLE host; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.host IS 'ORG:10,0';


--
-- Name: host_config; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.host_config (
    host_fk bigint NOT NULL,
    update_mode character(1),
    config_entry_base_fk bigint NOT NULL,
    CONSTRAINT host_cfg_host_fk_nn CHECK ((host_fk IS NOT NULL)),
    CONSTRAINT host_config_cfg_entry_base_nn CHECK ((config_entry_base_fk IS NOT NULL))
);


--
-- Name: TABLE host_config; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.host_config IS 'CONFIG:20,20';


--
-- Name: host_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.host_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 20;


--
-- Name: ihe_actor; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.ihe_actor (
    id bigint NOT NULL,
    name character varying(256) NOT NULL,
    type character varying(64) NOT NULL,
    company character varying(64),
    system character varying(64),
    receiver character varying(256),
    uid character varying(1024),
    associated_with_fk bigint,
    ihe_domain_fk integer
);


--
-- Name: TABLE ihe_actor; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.ihe_actor IS 'DATA:45,30';


--
-- Name: ihe_actor_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.ihe_actor_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: ihe_domain_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.ihe_domain_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 20;


--
-- Name: ihe_endpoint; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.ihe_endpoint (
    ihe_actor_fk bigint NOT NULL,
    transaction character varying(64) NOT NULL,
    uri character varying(512) NOT NULL,
    is_xua boolean DEFAULT false NOT NULL
);


--
-- Name: TABLE ihe_endpoint; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.ihe_endpoint IS 'DATA:38,15';


--
-- Name: ihe_manifest; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.ihe_manifest (
    dicom_image_fk bigint,
    generic_file_fk bigint,
    unique_id character varying(250) NOT NULL,
    file_checksum character varying(36) NOT NULL,
    patient_checksum character varying(36) NOT NULL,
    id bigint NOT NULL,
    ihe_domain_fk integer NOT NULL
);


--
-- Name: TABLE ihe_manifest; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.ihe_manifest IS 'DATA:60,10';


--
-- Name: ihe_manifest_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.ihe_manifest_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 20;


--
-- Name: image_marker; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.image_marker (
    id bigint NOT NULL,
    value character varying(1000) NOT NULL
);


--
-- Name: TABLE image_marker; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.image_marker IS 'KEYWORD:15,8';


--
-- Name: imedone_external_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.imedone_external_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 20;


--
-- Name: imedone_manifest; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.imedone_manifest (
    generic_file_fk bigint NOT NULL,
    file_checksum character varying(32) NOT NULL,
    id bigint NOT NULL,
    document_id character varying(64) NOT NULL,
    external_version_id character varying(256) NOT NULL,
    version_id character varying(64) NOT NULL
);


--
-- Name: TABLE imedone_manifest; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.imedone_manifest IS 'DATA:60,20';


--
-- Name: imedone_manifest_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.imedone_manifest_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 20;


--
-- Name: iocm_rejects; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.iocm_rejects (
    sop_instance_uid character varying(64) NOT NULL,
    updated_when timestamp without time zone NOT NULL,
    rejection_code_value integer NOT NULL
);


--
-- Name: TABLE iocm_rejects; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.iocm_rejects IS 'DATA:40,58';


--
-- Name: iocm_request_uid_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.iocm_request_uid_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 20;


--
-- Name: item_version_security; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.item_version_security (
    id bigint NOT NULL,
    tbl_item_version_fk bigint NOT NULL,
    provider character varying(400) NOT NULL,
    name character varying(400) NOT NULL,
    creation_date timestamp without time zone DEFAULT now(),
    item bytea,
    CONSTRAINT item_version_sec_cd_nn CHECK ((creation_date IS NOT NULL)),
    CONSTRAINT item_version_sec_n_nn CHECK ((name IS NOT NULL)),
    CONSTRAINT item_version_sec_p_nn CHECK ((provider IS NOT NULL)),
    CONSTRAINT item_version_sec_tiv_fk_nn CHECK ((tbl_item_version_fk IS NOT NULL))
);


--
-- Name: TABLE item_version_security; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.item_version_security IS 'ARCHIVE:10,0';


--
-- Name: item_version_security_prop; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.item_version_security_prop (
    id bigint NOT NULL,
    item_version_security_fk bigint,
    key character varying(255) NOT NULL,
    value character varying(255) NOT NULL,
    CONSTRAINT item_ver_sec_pro_key_nn CHECK ((key IS NOT NULL)),
    CONSTRAINT item_ver_sec_pro_val_nn CHECK ((value IS NOT NULL))
);


--
-- Name: TABLE item_version_security_prop; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.item_version_security_prop IS 'ARCHIVE:5,0';


--
-- Name: item_version_security_prop_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.item_version_security_prop_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 20;


--
-- Name: item_version_security_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.item_version_security_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 20;


--
-- Name: keyword; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.keyword (
    id bigint NOT NULL,
    parent_fk bigint,
    keyword_class_fk bigint NOT NULL,
    value character varying(4000),
    retired boolean DEFAULT false,
    weight bigint DEFAULT 0,
    obs_float_value double precision,
    timestamp_value timestamp without time zone,
    date_value date,
    num_value numeric(33,12),
    CONSTRAINT keyword_retired_nn CHECK ((retired IS NOT NULL)),
    CONSTRAINT keyword_weight_nn CHECK ((weight IS NOT NULL)),
    CONSTRAINT kw_kw_class_nn CHECK ((keyword_class_fk IS NOT NULL))
);


--
-- Name: TABLE keyword; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.keyword IS 'KEYWORD:10,0';


--
-- Name: keyword_class; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.keyword_class (
    id bigint NOT NULL,
    default_fk bigint,
    name character varying(60),
    type_fk character varying(255) NOT NULL,
    keyword_class_group_fk character varying(50),
    coding_scheme character varying(256),
    CONSTRAINT keyword_class_type_nn CHECK ((type_fk IS NOT NULL)),
    CONSTRAINT kw_class_name_nn CHECK ((name IS NOT NULL))
);


--
-- Name: TABLE keyword_class; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.keyword_class IS 'KEYWORD:10,10';


--
-- Name: keyword_class_constraint; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.keyword_class_constraint (
    keyword_class_fk bigint NOT NULL,
    keyword_class_const_type_fk character varying(50) NOT NULL,
    value character varying(4000),
    CONSTRAINT kwclassconstraint_kwclassfk_nn CHECK ((keyword_class_fk IS NOT NULL)),
    CONSTRAINT kwclassconstraint_typefk_nn CHECK ((keyword_class_const_type_fk IS NOT NULL))
);


--
-- Name: TABLE keyword_class_constraint; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.keyword_class_constraint IS 'KEYWORD:15,10';


--
-- Name: keyword_class_constraint_type; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.keyword_class_constraint_type (
    name character varying(50) NOT NULL,
    CONSTRAINT kwclassconstraint_type_name_nn CHECK ((name IS NOT NULL))
);


--
-- Name: TABLE keyword_class_constraint_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.keyword_class_constraint_type IS 'KEYWORD:20,10';


--
-- Name: keyword_class_group; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.keyword_class_group (
    name character varying(50) NOT NULL,
    description character varying(4000) NOT NULL,
    CONSTRAINT keyword_class_group_desc_nn CHECK ((description IS NOT NULL)),
    CONSTRAINT keyword_class_group_name_nn CHECK ((name IS NOT NULL))
);


--
-- Name: TABLE keyword_class_group; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.keyword_class_group IS 'KEYWORD:10,20';


--
-- Name: keyword_class_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.keyword_class_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 20;


--
-- Name: keyword_class_type; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.keyword_class_type (
    id character varying(255) NOT NULL
);


--
-- Name: TABLE keyword_class_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.keyword_class_type IS 'KEYWORD:15,15';


--
-- Name: keyword_display; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.keyword_display (
    id bigint NOT NULL,
    keyword_fk bigint NOT NULL,
    language_fk character varying(6) NOT NULL,
    display_name character varying(2048) NOT NULL,
    CONSTRAINT keyword_display_display_name_not_empty CHECK (((display_name)::text <> ''::text))
);


--
-- Name: TABLE keyword_display; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.keyword_display IS 'KEYWORD:0,0';


--
-- Name: keyword_display_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.keyword_display_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: keyword_level; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.keyword_level (
    name character varying(30) NOT NULL
);


--
-- Name: TABLE keyword_level; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.keyword_level IS 'KEYWORD:20,20';


--
-- Name: keyword_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.keyword_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 20;


--
-- Name: language; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.language (
    id character varying(6) NOT NULL,
    description character varying(256) NOT NULL
);


--
-- Name: TABLE language; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.language IS 'BACKEND:10,15';


--
-- Name: link_aet_aet; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.link_aet_aet (
    client_aet_fk bigint NOT NULL,
    modgrp_aet_fk bigint NOT NULL
);


--
-- Name: TABLE link_aet_aet; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.link_aet_aet IS 'ORG:10,20';


--
-- Name: link_aet_host; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.link_aet_host (
    aet_fk bigint NOT NULL,
    host_fk bigint NOT NULL,
    CONSTRAINT laethost_aetfk_nn CHECK ((aet_fk IS NOT NULL)),
    CONSTRAINT laethost_hostfk_nn CHECK ((host_fk IS NOT NULL))
);


--
-- Name: TABLE link_aet_host; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.link_aet_host IS 'ORG:10,5';


--
-- Name: link_aet_ignored_sop_class; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.link_aet_ignored_sop_class (
    aet_fk bigint NOT NULL,
    sop_class_fk smallint NOT NULL
);


--
-- Name: TABLE link_aet_ignored_sop_class; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.link_aet_ignored_sop_class IS 'ORG:5,10';


--
-- Name: link_aet_transfer_syntax; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.link_aet_transfer_syntax (
    aet_fk bigint NOT NULL,
    transfer_syntax_fk smallint NOT NULL,
    weight integer NOT NULL
);


--
-- Name: TABLE link_aet_transfer_syntax; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.link_aet_transfer_syntax IS 'ORG:5,5';


--
-- Name: link_diagnostic_report_fhir_identifier; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.link_diagnostic_report_fhir_identifier (
    diagnostic_report_fk bigint NOT NULL,
    fhir_identifier_fk bigint NOT NULL
);


--
-- Name: TABLE link_diagnostic_report_fhir_identifier; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.link_diagnostic_report_fhir_identifier IS 'FHIR:10,20';


--
-- Name: link_dicom_image_image_marker; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.link_dicom_image_image_marker (
    dicom_image_fk bigint NOT NULL,
    image_marker_fk bigint NOT NULL
);


--
-- Name: TABLE link_dicom_image_image_marker; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.link_dicom_image_image_marker IS 'KEYWORD:15,6';


--
-- Name: link_dicom_image_keyword; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.link_dicom_image_keyword (
    dicom_image_fk bigint NOT NULL,
    keyword_fk bigint NOT NULL,
    CONSTRAINT ldikw_di_fk_nn CHECK ((dicom_image_fk IS NOT NULL)),
    CONSTRAINT ldikw_keyword_fk_nn CHECK ((keyword_fk IS NOT NULL))
);
ALTER TABLE ONLY public.link_dicom_image_keyword ALTER COLUMN keyword_fk SET STATISTICS 10000;


--
-- Name: TABLE link_dicom_image_keyword; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.link_dicom_image_keyword IS 'KEYWORD:0,5';


--
-- Name: link_dicom_series_keyword; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.link_dicom_series_keyword (
    dicom_series_fk bigint NOT NULL,
    keyword_fk bigint NOT NULL,
    CONSTRAINT ldskw_ds_fk_nn CHECK ((dicom_series_fk IS NOT NULL)),
    CONSTRAINT ldskw_keyword_fk_nn CHECK ((keyword_fk IS NOT NULL))
);
ALTER TABLE ONLY public.link_dicom_series_keyword ALTER COLUMN keyword_fk SET STATISTICS 10000;


--
-- Name: TABLE link_dicom_series_keyword; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.link_dicom_series_keyword IS 'KEYWORD:0,10';


--
-- Name: link_document_class_group; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.link_document_class_group (
    document_class_fk bigint NOT NULL,
    document_class_group_fk bigint NOT NULL
);


--
-- Name: TABLE link_document_class_group; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.link_document_class_group IS 'DATA:30,80';


--
-- Name: link_document_class_keyword; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.link_document_class_keyword (
    document_class_fk bigint NOT NULL,
    keyword_fk bigint NOT NULL
);
ALTER TABLE ONLY public.link_document_class_keyword ALTER COLUMN keyword_fk SET STATISTICS 10000;


--
-- Name: TABLE link_document_class_keyword; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.link_document_class_keyword IS 'KEYWORD:0,23';


--
-- Name: link_document_document_marker; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.link_document_document_marker (
    document_fk bigint NOT NULL,
    document_marker_fk bigint NOT NULL
);


--
-- Name: TABLE link_document_document_marker; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.link_document_document_marker IS 'KEYWORD:20,6';


--
-- Name: link_document_keyword; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.link_document_keyword (
    document_fk bigint NOT NULL,
    keyword_fk bigint NOT NULL,
    CONSTRAINT ldkw_document_fk_nn CHECK ((document_fk IS NOT NULL)),
    CONSTRAINT ldkw_keyword_fk_nn CHECK ((keyword_fk IS NOT NULL))
);
ALTER TABLE ONLY public.link_document_keyword ALTER COLUMN keyword_fk SET STATISTICS 10000;


--
-- Name: TABLE link_document_keyword; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.link_document_keyword IS 'KEYWORD:0,15';


--
-- Name: link_document_keyword_all; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.link_document_keyword_all (
    document_fk bigint NOT NULL,
    keyword_fk bigint NOT NULL,
    usage_cnt bigint DEFAULT 1,
    CONSTRAINT ldkwa_document_fk_nn CHECK ((document_fk IS NOT NULL)),
    CONSTRAINT ldkwa_keyword_fk_nn CHECK ((keyword_fk IS NOT NULL)),
    CONSTRAINT ldkwa_usage_cnt_nn CHECK ((usage_cnt IS NOT NULL))
);
ALTER TABLE ONLY public.link_document_keyword_all ALTER COLUMN keyword_fk SET STATISTICS 10000;


--
-- Name: TABLE link_document_keyword_all; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.link_document_keyword_all IS 'KEYWORD:0,20';


--
-- Name: link_extension_extension_group; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.link_extension_extension_group (
    extension_fk character varying(50) NOT NULL,
    extension_group_fk character varying(100) NOT NULL,
    CONSTRAINT l_ext_ext_grp_e_fk_nn CHECK ((extension_fk IS NOT NULL)),
    CONSTRAINT l_ext_ext_grp_eg_fk_nn CHECK ((extension_group_fk IS NOT NULL))
);


--
-- Name: TABLE link_extension_extension_group; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.link_extension_extension_group IS 'BACKEND:40,10';


--
-- Name: link_generic_file_image_marker; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.link_generic_file_image_marker (
    generic_file_fk bigint NOT NULL,
    image_marker_fk bigint NOT NULL
);


--
-- Name: TABLE link_generic_file_image_marker; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.link_generic_file_image_marker IS 'KEYWORD:15,4';


--
-- Name: link_generic_file_keyword; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.link_generic_file_keyword (
    generic_file_fk bigint NOT NULL,
    keyword_fk bigint NOT NULL,
    CONSTRAINT lgfkw_gf_fk_nn CHECK ((generic_file_fk IS NOT NULL)),
    CONSTRAINT lgfkw_keyword_fk_nn CHECK ((keyword_fk IS NOT NULL))
);
ALTER TABLE ONLY public.link_generic_file_keyword ALTER COLUMN keyword_fk SET STATISTICS 10000;


--
-- Name: TABLE link_generic_file_keyword; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.link_generic_file_keyword IS 'KEYWORD:0,3';


--
-- Name: link_keyword_class_group_level; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.link_keyword_class_group_level (
    keyword_class_group_fk character varying(50) NOT NULL,
    keyword_level_fk character varying(30) NOT NULL,
    CONSTRAINT lkcl_keyword_class_group_nn CHECK ((keyword_class_group_fk IS NOT NULL)),
    CONSTRAINT lkcl_keyword_level_nn CHECK ((keyword_level_fk IS NOT NULL))
);


--
-- Name: TABLE link_keyword_class_group_level; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.link_keyword_class_group_level IS 'KEYWORD:15,20';


--
-- Name: link_medication_administration_fhir_identifier; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.link_medication_administration_fhir_identifier (
    medication_administration_fk bigint NOT NULL,
    fhir_identifier_fk bigint NOT NULL
);


--
-- Name: TABLE link_medication_administration_fhir_identifier; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.link_medication_administration_fhir_identifier IS 'FHIR:10,0';


--
-- Name: link_mpps_info_o_procedure; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.link_mpps_info_o_procedure (
    mpps_info_fk bigint NOT NULL,
    o_procedure_fk bigint NOT NULL
);


--
-- Name: TABLE link_mpps_info_o_procedure; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.link_mpps_info_o_procedure IS 'DATA:38,47';


--
-- Name: link_o_procedure_keyword; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.link_o_procedure_keyword (
    o_procedure_fk bigint NOT NULL,
    keyword_fk bigint NOT NULL
);
ALTER TABLE ONLY public.link_o_procedure_keyword ALTER COLUMN keyword_fk SET STATISTICS 10000;


--
-- Name: TABLE link_o_procedure_keyword; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.link_o_procedure_keyword IS 'KEYWORD:0,25';


--
-- Name: link_observation_fhir_identifier; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.link_observation_fhir_identifier (
    observation_fk bigint NOT NULL,
    fhir_identifier_fk bigint NOT NULL
);


--
-- Name: TABLE link_observation_fhir_identifier; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.link_observation_fhir_identifier IS 'FHIR:10,10';


--
-- Name: link_patient_visit; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.link_patient_visit (
    id bigint NOT NULL,
    patient_fk bigint NOT NULL,
    visit_fk bigint,
    CONSTRAINT lpv_patfk_nn CHECK ((patient_fk IS NOT NULL))
);


--
-- Name: TABLE link_patient_visit; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.link_patient_visit IS 'DATA:50,60';


--
-- Name: link_patient_visit_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.link_patient_visit_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 20;


--
-- Name: link_presentation_document; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.link_presentation_document (
    presentation_fk bigint NOT NULL,
    document_fk bigint NOT NULL,
    added_when timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE link_presentation_document; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.link_presentation_document IS 'PRES:10,10';


--
-- Name: link_report_item_version_security; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.link_report_item_version_security (
    item_version_security_fk bigint NOT NULL,
    report_fk bigint
);


--
-- Name: TABLE link_report_item_version_security; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.link_report_item_version_security IS 'ARCHIVE:5,2';


--
-- Name: link_role_pacs_user; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.link_role_pacs_user (
    role_fk character varying(255) NOT NULL,
    pacs_user_fk bigint NOT NULL,
    user_function character varying(255) DEFAULT ''::character varying NOT NULL,
    CONSTRAINT link_role_pacs_user_role_nn CHECK ((role_fk IS NOT NULL)),
    CONSTRAINT link_role_user_user_nn CHECK ((pacs_user_fk IS NOT NULL))
);


--
-- Name: TABLE link_role_pacs_user; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.link_role_pacs_user IS 'USER:0,10';


--
-- Name: link_role_permission; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.link_role_permission (
    id bigint NOT NULL,
    role_fk character varying(255) NOT NULL,
    permission_fk character varying(255) NOT NULL,
    CONSTRAINT link_role_perm_permission_nn CHECK ((permission_fk IS NOT NULL)),
    CONSTRAINT link_role_perm_role_nn CHECK ((role_fk IS NOT NULL)),
    CONSTRAINT link_role_permission_id_nn CHECK ((id IS NOT NULL))
);


--
-- Name: TABLE link_role_permission; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.link_role_permission IS 'USER:20,10';


--
-- Name: link_role_permission_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.link_role_permission_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 20;


--
-- Name: link_snapshot_document; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.link_snapshot_document (
    snapshot_fk bigint NOT NULL,
    document_fk bigint NOT NULL
);


--
-- Name: TABLE link_snapshot_document; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.link_snapshot_document IS 'PRES:10,0';


--
-- Name: link_storage_rule_tbl_node; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.link_storage_rule_tbl_node (
    tbl_node_fk bigint NOT NULL,
    storage_rule_fk bigint NOT NULL,
    node_type public.storage_rule_node_type NOT NULL
);


--
-- Name: TABLE link_storage_rule_tbl_node; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.link_storage_rule_tbl_node IS 'ARCHIVE:0,5';


--
-- Name: link_tbl_node_tbl_item_version_fsnode_archive_object; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.link_tbl_node_tbl_item_version_fsnode_archive_object (
    tbl_node_fk bigint NOT NULL,
    tbl_item_version_fk bigint NOT NULL,
    fsnode_archive_object_fk bigint
);


--
-- Name: TABLE link_tbl_node_tbl_item_version_fsnode_archive_object; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.link_tbl_node_tbl_item_version_fsnode_archive_object IS 'ARCHIVE:20,0';


--
-- Name: marker_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.marker_seq
    START WITH 3
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: medication_administration; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.medication_administration (
    id bigint NOT NULL,
    fhir jsonb NOT NULL,
    generic_file_fk bigint,
    effective_date_time timestamp with time zone,
    effective_period tstzrange,
    fhir_version smallint
);


--
-- Name: TABLE medication_administration; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.medication_administration IS 'FHIR:20,0';


--
-- Name: medication_administration_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.medication_administration_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: modality; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.modality (
    id character varying(20) NOT NULL,
    description character varying(200),
    CONSTRAINT modality_upper_cc CHECK (((id)::text = upper((id)::text)))
);


--
-- Name: TABLE modality; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.modality IS 'DATA:30,30';


--
-- Name: modifier; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.modifier (
    id smallint NOT NULL,
    description character varying(200),
    priority bigint DEFAULT 0,
    CONSTRAINT modi_priority_nn CHECK ((priority IS NOT NULL))
);


--
-- Name: TABLE modifier; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.modifier IS 'DATA:70,60';


--
-- Name: mpps_image_info; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.mpps_image_info (
    id bigint NOT NULL,
    sop_instance_uid character varying(250) NOT NULL,
    sop_class_uid character varying(250) NOT NULL,
    mpps_series_info_fk bigint NOT NULL
);


--
-- Name: TABLE mpps_image_info; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.mpps_image_info IS 'DATA:28,40';


--
-- Name: mpps_image_info_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.mpps_image_info_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: mpps_info; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.mpps_info (
    id bigint NOT NULL,
    mpps_sop_instance_uid character varying(250) NOT NULL,
    state public.mpps_state NOT NULL,
    start_time timestamp without time zone NOT NULL,
    updated_when timestamp without time zone DEFAULT now() NOT NULL,
    study_instance_uid character varying(250) NOT NULL,
    accession_number character varying(50)[] NOT NULL,
    dose_report_created boolean DEFAULT false NOT NULL,
    calling_host character varying(255) NOT NULL,
    calling_aet character varying(255) NOT NULL,
    class character varying(250) NOT NULL,
    ian_sent boolean DEFAULT false NOT NULL
);


--
-- Name: TABLE mpps_info; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.mpps_info IS 'DATA:30,45';


--
-- Name: mpps_info_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.mpps_info_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: mpps_series_info; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.mpps_series_info (
    id bigint NOT NULL,
    series_instance_uid character varying(250) NOT NULL,
    mpps_info_fk bigint NOT NULL
);


--
-- Name: TABLE mpps_series_info; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.mpps_series_info IS 'DATA:28,42';


--
-- Name: mpps_series_info_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.mpps_series_info_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: node_info; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.node_info (
    tbl_node_fk bigint NOT NULL,
    bytes_total bigint NOT NULL,
    bytes_free bigint NOT NULL,
    bytes_reserved bigint NOT NULL,
    bytes_master bigint NOT NULL,
    bytes_replica bigint NOT NULL,
    inodes_total bigint,
    inodes_free bigint,
    fs_type character varying(100),
    last_updated timestamp without time zone,
    CONSTRAINT nodinf_byfree_cc CHECK ((bytes_free >= '-1'::integer)),
    CONSTRAINT nodinf_bymaster_cc CHECK ((bytes_master >= '-1'::integer)),
    CONSTRAINT nodinf_byrepil_cc CHECK ((bytes_replica >= '-1'::integer)),
    CONSTRAINT nodinf_byreserv_cc CHECK ((bytes_reserved >= '-1'::integer)),
    CONSTRAINT nodinf_bytesfree_nn CHECK ((bytes_free IS NOT NULL)),
    CONSTRAINT nodinf_bytesmast_nn CHECK ((bytes_master IS NOT NULL)),
    CONSTRAINT nodinf_bytesrepl_nn CHECK ((bytes_replica IS NOT NULL)),
    CONSTRAINT nodinf_bytesres_nn CHECK ((bytes_reserved IS NOT NULL)),
    CONSTRAINT nodinf_bytestot_nn CHECK ((bytes_total IS NOT NULL)),
    CONSTRAINT nodinf_bytotal_cc CHECK ((bytes_total >= '-1'::integer)),
    CONSTRAINT nodinf_nodefk_nn CHECK ((tbl_node_fk IS NOT NULL))
);


--
-- Name: TABLE node_info; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.node_info IS 'ARCHIVE:0,10';


--
-- Name: notification; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.notification (
    id bigint NOT NULL,
    document_fk bigint NOT NULL,
    name character varying(255) NOT NULL,
    inserted_when timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone,
    notification_event_fk character(1) NOT NULL,
    processed_when timestamp without time zone,
    processed smallint DEFAULT 0,
    data1 character varying(1000),
    data2 character varying(1000),
    data3 character varying(1000),
    data4 character varying(1000),
    data5 character varying(1000),
    CONSTRAINT notif_docfk_nn CHECK ((document_fk IS NOT NULL)),
    CONSTRAINT notif_eventfk_nn CHECK ((notification_event_fk IS NOT NULL)),
    CONSTRAINT notif_id_nn CHECK ((id IS NOT NULL)),
    CONSTRAINT notif_inswhen_nn CHECK ((inserted_when IS NOT NULL)),
    CONSTRAINT notif_name_nn CHECK ((name IS NOT NULL)),
    CONSTRAINT notif_processed_chk CHECK ((processed = ANY (ARRAY[0, 1, 2]))),
    CONSTRAINT notif_processed_nn CHECK ((processed IS NOT NULL)),
    CONSTRAINT notification_name_not_empty CHECK (((name)::text <> ''::text))
);


--
-- Name: TABLE notification; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.notification IS 'BACKEND:40,0';


--
-- Name: notification_event; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.notification_event (
    id character(1) NOT NULL,
    description character varying(20),
    CONSTRAINT notif_eve_id_nn CHECK ((id IS NOT NULL))
);


--
-- Name: TABLE notification_event; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.notification_event IS 'BACKEND:50,0';


--
-- Name: notification_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.notification_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: notification_type; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.notification_type (
    id bigint NOT NULL,
    name character varying(255) NOT NULL,
    description character varying(255),
    notification_event_fk character(1) NOT NULL,
    CONSTRAINT no_ty_event_fk_nn CHECK ((notification_event_fk IS NOT NULL)),
    CONSTRAINT no_ty_name_nn CHECK ((name IS NOT NULL)),
    CONSTRAINT notif_typ_id_nn CHECK ((id IS NOT NULL)),
    CONSTRAINT notification_event_not_auto CHECK ((notification_event_fk <> 'A'::bpchar)),
    CONSTRAINT notification_type_name_not_empty CHECK (((name)::text <> ''::text))
);


--
-- Name: TABLE notification_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.notification_type IS 'BACKEND:60,0';


--
-- Name: notification_type_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.notification_type_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 20;


--
-- Name: o_procedure_accession_number_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.o_procedure_accession_number_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    MAXVALUE 9999999999999
    CACHE 1;


--
-- Name: o_procedure_procedure_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.o_procedure_procedure_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    MAXVALUE 9999999999999
    CACHE 1;


--
-- Name: o_procedure_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.o_procedure_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: o_procedure_step_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.o_procedure_step_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    MAXVALUE 9999999999999
    CACHE 1;


--
-- Name: o_procedure; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.o_procedure (
    id bigint NOT NULL,
    accession_number character varying(16) DEFAULT ('SYN'::text || nextval('public.o_procedure_accession_number_seq'::regclass)) NOT NULL,
    domain character varying(20),
    external_id character varying(255) DEFAULT currval('public.o_procedure_seq'::regclass) NOT NULL,
    filler_order_number character varying(200),
    filled boolean DEFAULT false,
    inserted_when timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
    last_modified_when timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
    link_patient_visit_fk bigint NOT NULL,
    modality_fk character varying(20),
    orderer_fk bigint,
    patient_size numeric(25,6),
    patient_weight numeric(25,6),
    performing_physician character varying(350),
    placer_order_number character varying(200),
    procedure_comments character varying(4000),
    procedure_description character varying(64),
    procedure_id character varying(16) DEFAULT ('SYN'::text || nextval('public.o_procedure_procedure_id_seq'::regclass)),
    referring_physician character varying(350),
    requesting_physician character varying(350),
    step_code_value character varying(16),
    step_code_meaning character varying(64),
    step_coding_scheme character varying(16),
    step_description character varying(64),
    step_id character varying(16) DEFAULT ('SYN'::text || nextval('public.o_procedure_step_id_seq'::regclass)),
    step_start_date timestamp without time zone,
    scheduled_station_fk bigint,
    study_instance_uid character varying(250) DEFAULT ((((('1.3.6.1.4.1.24930.2.'::text || public.get_serial_number()) || '.'::text) || ((date_part('epoch'::text, LOCALTIMESTAMP) * (1000)::double precision))::bigint) || '.'::text) || currval('public.o_procedure_seq'::regclass)) NOT NULL,
    state public.order_state DEFAULT 'SC'::public.order_state NOT NULL,
    document_class_fk bigint,
    admitting_diagnosis_description character varying(64),
    procedure_reason character varying(64),
    institutional_department_name character varying(64),
    CONSTRAINT accession_number_not_empty CHECK (((accession_number)::text <> ''::text)),
    CONSTRAINT o_procedure_procedure_id_nn CHECK ((procedure_id IS NOT NULL)),
    CONSTRAINT o_procedure_step_id_nn CHECK ((step_id IS NOT NULL))
);


--
-- Name: TABLE o_procedure; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.o_procedure IS 'ORNG:0,10';


--
-- Name: observation; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.observation (
    id bigint NOT NULL,
    fhir jsonb NOT NULL,
    diagnostic_report_fk bigint,
    generic_file_fk bigint,
    effective_date_time timestamp with time zone,
    effective_period tstzrange,
    value_date_time timestamp with time zone,
    fhir_version smallint,
    specimen_accession_identifier text,
    specimen_collected_date_time timestamp with time zone,
    specimen_collected_period tstzrange,
    specimen_status text,
    weight bigint
);


--
-- Name: TABLE observation; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.observation IS 'FHIR:20,10';


--
-- Name: observation_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.observation_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: order_additional_info; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.order_additional_info (
    order_entry_fk bigint NOT NULL,
    key character varying(100) NOT NULL,
    value character varying(4000)
);


--
-- Name: TABLE order_additional_info; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.order_additional_info IS 'ORDER:10,20';


--
-- Name: order_entry; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.order_entry (
    id bigint NOT NULL,
    external_id character varying(255) NOT NULL,
    order_entry_fk bigint,
    order_status_fk character varying(10) NOT NULL,
    service_location_fk character varying(255),
    effective_when_begin timestamp without time zone,
    effective_when_end timestamp without time zone,
    modality_fk character varying(20),
    description character varying(255),
    code character varying(255),
    accession_number character varying(16),
    study_instance_uid character varying(255),
    series_instance_uid character varying(255),
    img_cnt integer DEFAULT 0,
    CONSTRAINT order_e_extid_nn CHECK ((external_id IS NOT NULL)),
    CONSTRAINT order_e_img_cnt_nn CHECK ((img_cnt IS NOT NULL)),
    CONSTRAINT order_e_o_s_fk_nn CHECK ((order_status_fk IS NOT NULL))
);
ALTER TABLE ONLY public.order_entry ALTER COLUMN order_entry_fk SET STATISTICS 10000;


--
-- Name: TABLE order_entry; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.order_entry IS 'ORDER:0,10';


--
-- Name: order_entry_history; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.order_entry_history (
    order_status_fk character varying(10) NOT NULL,
    order_entry_fk bigint NOT NULL,
    external_id character varying(255) NOT NULL,
    begin_when timestamp without time zone NOT NULL,
    img_cnt integer DEFAULT 0,
    modifier_fk smallint NOT NULL,
    info character varying(4000),
    CONSTRAINT order_e_h_begin_nn CHECK ((begin_when IS NOT NULL)),
    CONSTRAINT order_e_h_extid_nn CHECK ((external_id IS NOT NULL)),
    CONSTRAINT order_e_h_fk_nn CHECK ((order_entry_fk IS NOT NULL)),
    CONSTRAINT order_e_h_imgcnt_nn CHECK ((img_cnt IS NOT NULL)),
    CONSTRAINT order_e_h_modi_fk_nn CHECK ((modifier_fk IS NOT NULL)),
    CONSTRAINT order_e_h_status_fk_nn CHECK ((order_status_fk IS NOT NULL))
);
ALTER TABLE ONLY public.order_entry_history ALTER COLUMN external_id SET STATISTICS 10000;


--
-- Name: TABLE order_entry_history; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.order_entry_history IS 'ORDER:0,0';


--
-- Name: order_entry_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.order_entry_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: order_root; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.order_root (
    order_entry_fk bigint NOT NULL,
    domain character varying(255) NOT NULL,
    external_id character varying(255) NOT NULL,
    depth smallint NOT NULL,
    link_patient_visit_fk bigint NOT NULL,
    CONSTRAINT order_root_depth_nn CHECK ((depth IS NOT NULL)),
    CONSTRAINT order_root_domain_nn CHECK ((domain IS NOT NULL)),
    CONSTRAINT order_root_external_id_nn CHECK ((external_id IS NOT NULL)),
    CONSTRAINT order_root_lpv_fk_nn CHECK ((link_patient_visit_fk IS NOT NULL))
);


--
-- Name: TABLE order_root; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.order_root IS 'ORDER:0,20';


--
-- Name: order_status; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.order_status (
    code character varying(10) NOT NULL,
    description character varying(4000),
    show_worklist boolean DEFAULT false,
    CONSTRAINT order_status_s_w_nn CHECK ((show_worklist IS NOT NULL))
);


--
-- Name: TABLE order_status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.order_status IS 'ORDER:10,0';


--
-- Name: orderer; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.orderer (
    orgunit_fk character varying(50),
    type_fk character varying(255) NOT NULL,
    order_entry_fk bigint NOT NULL,
    abbrevation character varying(255) NOT NULL,
    CONSTRAINT orderer_abbrevation_nn CHECK ((abbrevation IS NOT NULL)),
    CONSTRAINT orderer_order_e_fk_nn CHECK ((order_entry_fk IS NOT NULL)),
    CONSTRAINT orderer_type_fk_nn CHECK ((type_fk IS NOT NULL))
);


--
-- Name: TABLE orderer; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.orderer IS 'ORDER:10,10';


--
-- Name: orderer_type; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.orderer_type (
    name character varying(255) NOT NULL,
    description character varying(4000)
);


--
-- Name: TABLE orderer_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.orderer_type IS 'ORDER:10,15';


--
-- Name: orgunit; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.orgunit (
    id bigint NOT NULL,
    name character varying(255) NOT NULL,
    abk character varying(50) NOT NULL,
    orgunit_fk bigint,
    alt_id1 character varying(500),
    alt_id2 character varying(500),
    alt_id3 character varying(500),
    alt_info1 character varying(500),
    alt_info2 character varying(500),
    alt_info3 character varying(500),
    inserted_when timestamp without time zone,
    inserted_by_fk smallint,
    last_modified_when timestamp without time zone,
    last_modified_by_fk smallint,
    is_producer boolean,
    view_by_pid_protection boolean DEFAULT true,
    is_orderer boolean DEFAULT false NOT NULL,
    CONSTRAINT orgu_abk_nn CHECK ((abk IS NOT NULL)),
    CONSTRAINT orgu_isproducer_nn CHECK ((is_producer IS NOT NULL)),
    CONSTRAINT orgu_name_nn CHECK ((name IS NOT NULL)),
    CONSTRAINT orgunit_no_cycles CHECK (public.orgunit_check_no_cycles(id, orgunit_fk)),
    CONSTRAINT orgunit_v_b_p_p_nn CHECK ((view_by_pid_protection IS NOT NULL))
);


--
-- Name: TABLE orgunit; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.orgunit IS 'ORG:0,0';


--
-- Name: orgunit_config; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.orgunit_config (
    orgunit_fk bigint NOT NULL,
    config_entry_base_fk bigint NOT NULL,
    CONSTRAINT orgunit_cfg_cfg_entry_base_nn CHECK ((config_entry_base_fk IS NOT NULL)),
    CONSTRAINT orgunit_cfg_orgunit_nn CHECK ((orgunit_fk IS NOT NULL))
);


--
-- Name: TABLE orgunit_config; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.orgunit_config IS 'CONFIG:0,20';


--
-- Name: orgunit_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.orgunit_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 20;


--
-- Name: orgunit_tree; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.orgunit_tree AS
 WITH RECURSIVE sub AS (
         SELECT orgunit.id,
            orgunit.abk,
            orgunit.name,
            orgunit.alt_id1,
            orgunit.alt_info1 AS description,
            orgunit.abk AS orgunit_ancestor_abk,
            orgunit.id AS orgunit_ancestor_id,
            1 AS depth
           FROM public.orgunit
        UNION ALL
         SELECT ou.id,
            ou.abk,
            ou.name,
            ou.alt_id1,
            ou.alt_info1 AS description,
            sd.orgunit_ancestor_abk,
            sd.orgunit_ancestor_id,
            (sd.depth + 1) AS depth
           FROM (public.orgunit ou
             JOIN sub sd ON ((ou.orgunit_fk = sd.id)))
        )
 SELECT sub.id,
    sub.abk,
    sub.name,
    sub.alt_id1,
    sub.description,
    sub.orgunit_ancestor_abk,
    sub.orgunit_ancestor_id,
    sub.depth
   FROM sub
  ORDER BY sub.abk, sub.depth;


--
-- Name: pacs_session; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE UNLOGGED TABLE public.pacs_session (
    id bigint NOT NULL,
    pacs_user_fk bigint,
    begins_when timestamp without time zone,
    application character varying(100),
    host_fk bigint,
    last_keep_alive timestamp without time zone,
    permission_clause text,
    user_function character varying(255) DEFAULT ''::character varying NOT NULL
);


--
-- Name: TABLE pacs_session; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.pacs_session IS 'USER:10,0';


--
-- Name: pacs_session_parameter; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE UNLOGGED TABLE public.pacs_session_parameter (
    pacs_session_permission_fk bigint NOT NULL,
    parameter character varying(30) NOT NULL,
    value character varying(4000),
    CONSTRAINT pacssessparam_pacssessperm_nn CHECK ((pacs_session_permission_fk IS NOT NULL)),
    CONSTRAINT pacssessparam_parameter_nn CHECK ((parameter IS NOT NULL))
);


--
-- Name: TABLE pacs_session_parameter; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.pacs_session_parameter IS 'USER:30,0';


--
-- Name: pacs_session_permission; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE UNLOGGED TABLE public.pacs_session_permission (
    id bigint NOT NULL,
    pacs_session_fk bigint NOT NULL,
    permission_fk character varying(255) NOT NULL,
    flat boolean NOT NULL,
    CONSTRAINT pacssessperm_flat_nn CHECK ((flat IS NOT NULL)),
    CONSTRAINT pacssessperm_pacssess_nn CHECK ((pacs_session_fk IS NOT NULL)),
    CONSTRAINT pacssessperm_permission_nn CHECK ((permission_fk IS NOT NULL))
);


--
-- Name: TABLE pacs_session_permission; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.pacs_session_permission IS 'USER:20,0';


--
-- Name: pacs_user; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.pacs_user (
    id bigint NOT NULL,
    salutation character varying(20),
    title character varying(50),
    firstname character varying(255) NOT NULL,
    lastname character varying(255) NOT NULL,
    login character varying(255) NOT NULL,
    passwd character varying(255) NOT NULL,
    employeeid character varying(255),
    isactive boolean DEFAULT false,
    inserted_when timestamp without time zone,
    inserted_by_fk smallint,
    last_modified_when timestamp without time zone,
    last_modified_by_fk smallint,
    email character varying(255),
    telefon character varying(255),
    pager character varying(255),
    birth_date timestamp without time zone,
    entry_date timestamp without time zone,
    separation_date timestamp without time zone,
    password_hash character varying(64),
    password_salt character varying(64),
    patient_fk bigint,
    sms character varying(255),
    last_active_when timestamp without time zone,
    CONSTRAINT pacs_user_isactive_nn CHECK ((isactive IS NOT NULL)),
    CONSTRAINT "puser_$$$$_nn" CHECK ((login IS NOT NULL)),
    CONSTRAINT puser_firstname_nn CHECK ((firstname IS NOT NULL)),
    CONSTRAINT puser_lastname_nn CHECK ((lastname IS NOT NULL)),
    CONSTRAINT puser_passwd_nn CHECK ((passwd IS NOT NULL))
);


--
-- Name: TABLE pacs_user; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.pacs_user IS 'USER:0,0';


--
-- Name: parameter; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.parameter (
    link_role_permission_fk bigint NOT NULL,
    permission_parameter_desc_fk bigint NOT NULL,
    string_value character varying(4000),
    CONSTRAINT parameter_lrp_nn CHECK ((link_role_permission_fk IS NOT NULL)),
    CONSTRAINT parameter_ppd_nn CHECK ((permission_parameter_desc_fk IS NOT NULL))
);


--
-- Name: TABLE parameter; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.parameter IS 'USER:30,10';


--
-- Name: permission_parameter_desc; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.permission_parameter_desc (
    id bigint NOT NULL,
    permission_fk character varying(255) NOT NULL,
    name character varying(30) NOT NULL,
    type_fk character varying(30) NOT NULL,
    constrain character varying(255),
    CONSTRAINT perm_param_desc_name_nn CHECK ((name IS NOT NULL)),
    CONSTRAINT perm_param_desc_perm_nn CHECK ((permission_fk IS NOT NULL)),
    CONSTRAINT perm_param_desc_type_nn CHECK ((type_fk IS NOT NULL)),
    CONSTRAINT permission_param_desc_id_nn CHECK ((id IS NOT NULL))
);


--
-- Name: TABLE permission_parameter_desc; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.permission_parameter_desc IS 'USER:40,10';


--
-- Name: role; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.role (
    name character varying(255) NOT NULL,
    description character varying(255),
    CONSTRAINT role_name_nn CHECK ((name IS NOT NULL)),
    CONSTRAINT role_name_not_empty CHECK (((name)::text <> ''::text))
);


--
-- Name: TABLE role; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.role IS 'USER:10,10';


--
-- Name: pacs_session_permission_flat; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.pacs_session_permission_flat AS
 SELECT pacs_session.id AS session_fk,
    link_role_permission.id AS link_role_permission_fk,
    link_role_permission.permission_fk,
    orgunit_tree.abk,
    orgunit_tree.depth,
    link_role_pacs_user.user_function
   FROM (((((((public.pacs_session
     JOIN public.pacs_user ON ((pacs_session.pacs_user_fk = pacs_user.id)))
     JOIN public.link_role_pacs_user ON ((link_role_pacs_user.pacs_user_fk = pacs_user.id)))
     JOIN public.role ON (((link_role_pacs_user.role_fk)::text = (role.name)::text)))
     JOIN public.link_role_permission ON (((link_role_permission.role_fk)::text = (role.name)::text)))
     JOIN public.parameter ON ((parameter.link_role_permission_fk = link_role_permission.id)))
     JOIN public.permission_parameter_desc ON ((parameter.permission_parameter_desc_fk = permission_parameter_desc.id)))
     JOIN public.orgunit_tree ON (((orgunit_tree.orgunit_ancestor_abk)::text = (parameter.string_value)::text)))
  WHERE ((parameter.link_role_permission_fk IN ( SELECT p_i.link_role_permission_fk
           FROM ((public.link_role_permission lrpe_i
             JOIN public.parameter p_i ON ((p_i.link_role_permission_fk = lrpe_i.id)))
             JOIN public.permission_parameter_desc ppd_i ON ((p_i.permission_parameter_desc_fk = ppd_i.id)))
          WHERE (((ppd_i.name)::text = 'subtree'::text) AND ((p_i.string_value)::text = '1'::text)))) AND (orgunit_tree.depth <> 1) AND ((permission_parameter_desc.name)::text = 'orgUnit'::text));


--
-- Name: pacs_session_permission_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.pacs_session_permission_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 20;


--
-- Name: pacs_user_attribute; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.pacs_user_attribute (
    id bigint NOT NULL,
    pacs_user_fk bigint NOT NULL,
    key character varying(255) NOT NULL,
    value character varying(255)
);


--
-- Name: TABLE pacs_user_attribute; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.pacs_user_attribute IS 'USER:10,5';


--
-- Name: pacs_user_attribute_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.pacs_user_attribute_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: pacs_user_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.pacs_user_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 20;


--
-- Name: pacs_user_token; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.pacs_user_token (
    id bigint NOT NULL,
    pacs_user_fk bigint NOT NULL,
    token character varying(255) NOT NULL,
    valid_until timestamp without time zone DEFAULT (('now'::text)::timestamp without time zone + '01:00:00'::interval) NOT NULL,
    valid_uses bigint DEFAULT 1 NOT NULL,
    purpose character varying(255) DEFAULT 'login'::character varying NOT NULL
);


--
-- Name: TABLE pacs_user_token; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.pacs_user_token IS 'USER:10,3';


--
-- Name: pacs_user_token_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.pacs_user_token_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: patient; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.patient (
    id bigint NOT NULL,
    sex_fk character(1),
    patient_invalidated_by_fk bigint,
    patient_id character varying(64) NOT NULL,
    last_name character varying(100),
    first_name character varying(100) DEFAULT ''::character varying,
    title character varying(50),
    birth_date date,
    status_deleted smallint NOT NULL,
    inserted_when timestamp without time zone NOT NULL,
    inserted_by_fk smallint NOT NULL,
    last_modified_when timestamp without time zone NOT NULL,
    last_modified_by_fk smallint NOT NULL,
    deleted_when timestamp without time zone,
    deleted_by_fk smallint,
    num_dicom bigint DEFAULT 0,
    num_generics bigint DEFAULT 0,
    vip_indicator_fk character(2) DEFAULT '-'::bpchar,
    CONSTRAINT pat_id_not_empty CHECK (((patient_id)::text <> ''::text)),
    CONSTRAINT pat_ins_by_fk_nn CHECK ((inserted_by_fk IS NOT NULL)),
    CONSTRAINT pat_ins_when_fk_nn CHECK ((inserted_when IS NOT NULL)),
    CONSTRAINT pat_last_mod_by_fk_nn CHECK ((last_modified_by_fk IS NOT NULL)),
    CONSTRAINT pat_last_mod_when_fk_nn CHECK ((last_modified_when IS NOT NULL)),
    CONSTRAINT pat_num_dicom_nn CHECK ((num_dicom IS NOT NULL)),
    CONSTRAINT pat_num_generics_nn CHECK ((num_generics IS NOT NULL)),
    CONSTRAINT pat_patient_id_nn CHECK ((patient_id IS NOT NULL)),
    CONSTRAINT pat_sex_nn CHECK ((sex_fk IS NOT NULL)),
    CONSTRAINT pat_status_deleted_nn CHECK ((status_deleted IS NOT NULL)),
    CONSTRAINT pat_vip_ind_nn CHECK ((vip_indicator_fk IS NOT NULL)),
    CONSTRAINT patient_first_name_nn CHECK ((first_name IS NOT NULL)),
    CONSTRAINT patient_last_name_nn CHECK ((last_name IS NOT NULL))
);


--
-- Name: TABLE patient; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.patient IS 'DATA:40,70';


--
-- Name: patient_class; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.patient_class (
    id character varying(2) NOT NULL,
    description character varying(200)
);


--
-- Name: TABLE patient_class; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.patient_class IS 'DATA:55,70';


--
-- Name: patient_marker_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.patient_marker_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: patient_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.patient_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: permission; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.permission (
    id bigint NOT NULL,
    name character varying(255) NOT NULL,
    description character varying(255) NOT NULL,
    CONSTRAINT per_des_nn CHECK ((description IS NOT NULL)),
    CONSTRAINT per_nam_nn CHECK ((name IS NOT NULL))
);


--
-- Name: TABLE permission; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.permission IS 'USER:30,5';


--
-- Name: permission_param_sel_value_a; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.permission_param_sel_value_a AS
 SELECT ppd.id AS permission_parameter_desc_fk,
    ou.abk AS description,
    ou.abk AS value
   FROM public.orgunit ou,
    ( SELECT unnest(ARRAY[2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 27, 28, 30, 35, 40, 44, 46, 57, 59, 62, 65, 68, 70, 72, 75]) AS id) ppd
UNION ALL
 SELECT ppd.id AS permission_parameter_desc_fk,
    document_marker.value AS description,
    (document_marker.id)::character varying AS value
   FROM public.document_marker,
    ( SELECT unnest(ARRAY[61, 64]) AS id) ppd
UNION ALL
 SELECT ppd.id AS permission_parameter_desc_fk,
    document_class_group.name AS description,
    (document_class_group.id)::text AS value
   FROM public.document_class_group,
    ( SELECT unnest(ARRAY[(67)::bigint, (74)::bigint, (77)::bigint]) AS id) ppd;


--
-- Name: permission_param_sel_value_b; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.permission_param_sel_value_b AS
 SELECT ppd.id AS permission_parameter_desc_fk,
    kwcg.name AS description,
    kwcg.name AS value
   FROM ( SELECT DISTINCT keyword_class_group.name
           FROM public.keyword_class_group) kwcg,
    ( SELECT permission_parameter_desc.id
           FROM public.permission_parameter_desc
          WHERE (((permission_parameter_desc.name)::text = 'keywordGroup'::text) AND ((permission_parameter_desc.type_fk)::text = 'SELECT'::text) AND ((permission_parameter_desc.permission_fk)::text = ANY (ARRAY[('SearchKeywords'::character varying)::text, ('ReadKeywords'::character varying)::text, ('ModifyKeywords'::character varying)::text])))) ppd
UNION ALL
 SELECT ppd.id AS permission_parameter_desc_fk,
    kwc.name AS description,
    kwc.name AS value
   FROM ( SELECT DISTINCT keyword_class.name
           FROM public.keyword_class) kwc,
    ( SELECT permission_parameter_desc.id
           FROM public.permission_parameter_desc
          WHERE (((permission_parameter_desc.name)::text = 'serviceDescription'::text) AND ((permission_parameter_desc.type_fk)::text = 'SELECT'::text) AND ((permission_parameter_desc.permission_fk)::text = 'ManageOrders'::text))) ppd
UNION ALL
 SELECT ppd.id AS permission_parameter_desc_fk,
    kwc.name AS description,
    kwc.value
   FROM ( SELECT '-'::character varying AS name,
            NULL::character varying AS value
        UNION ALL
         SELECT DISTINCT keyword_class.name,
            keyword_class.name AS value
           FROM public.keyword_class) kwc,
    ( SELECT permission_parameter_desc.id
           FROM public.permission_parameter_desc
          WHERE (((permission_parameter_desc.name)::text = 'descriptionKeywordClass'::text) AND ((permission_parameter_desc.type_fk)::text = 'SELECT'::text) AND ((permission_parameter_desc.permission_fk)::text = 'ArchiveDocument'::text))) ppd;


--
-- Name: permission_param_sel_value_c; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.permission_param_sel_value_c AS
 SELECT ppd.id AS permission_parameter_desc_fk,
    aet.name AS description,
    aet.name AS value
   FROM ( SELECT DISTINCT aet_1.name,
            aet_1.description
           FROM public.aet aet_1) aet,
    ( SELECT permission_parameter_desc.id
           FROM public.permission_parameter_desc
          WHERE (((permission_parameter_desc.name)::text = 'aet'::text) AND ((permission_parameter_desc.type_fk)::text = 'SELECT'::text) AND ((permission_parameter_desc.permission_fk)::text = ANY (ARRAY[('ManageOrders'::character varying)::text, ('ViewWorklist'::character varying)::text])))) ppd
UNION ALL
 SELECT ppd.id AS permission_parameter_desc_fk,
    kwcg.name AS description,
    kwcg.value
   FROM ( SELECT DISTINCT keyword_class_group.name,
            keyword_class_group.name AS value
           FROM public.keyword_class_group
        UNION ALL
         SELECT '-'::character varying AS name,
            NULL::character varying AS value) kwcg,
    ( SELECT permission_parameter_desc.id
           FROM public.permission_parameter_desc
          WHERE (((permission_parameter_desc.name)::text = 'keywordGroup'::text) AND ((permission_parameter_desc.type_fk)::text = 'SELECT'::text) AND ((permission_parameter_desc.permission_fk)::text = 'KeywordAdministration'::text))) ppd;


--
-- Name: vip_indicator; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.vip_indicator (
    id character(2) NOT NULL,
    description character varying(200)
);


--
-- Name: TABLE vip_indicator; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.vip_indicator IS 'DATA:45,60';


--
-- Name: permission_param_sel_value_d; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.permission_param_sel_value_d AS
 SELECT ppd.id AS permission_parameter_desc_fk,
    vip.description,
    "left"(((vip.id)::text || ' '::text), 2) AS value
   FROM public.vip_indicator vip,
    ( SELECT (48)::bigint AS id) ppd
UNION ALL
 SELECT ppd.id AS permission_parameter_desc_fk,
    ap.description,
    ap.value
   FROM ( SELECT '-'::text AS description,
            NULL::text AS value
        UNION ALL
         SELECT 'ADT'::text AS description,
            'ADT'::text AS value
        UNION ALL
         SELECT 'non ADT'::text AS description,
            'non ADT'::text AS value) ap,
    ( SELECT (51)::bigint AS id) ppd;


--
-- Name: permission_param_sel_value; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.permission_param_sel_value AS
 SELECT permission_param_sel_value_a.permission_parameter_desc_fk,
    permission_param_sel_value_a.description,
    permission_param_sel_value_a.value,
    0 AS weight
   FROM public.permission_param_sel_value_a
UNION ALL
 SELECT permission_param_sel_value_b.permission_parameter_desc_fk,
    permission_param_sel_value_b.description,
    permission_param_sel_value_b.value,
    0 AS weight
   FROM public.permission_param_sel_value_b
UNION ALL
 SELECT permission_param_sel_value_c.permission_parameter_desc_fk,
    permission_param_sel_value_c.description,
    permission_param_sel_value_c.value,
    0 AS weight
   FROM public.permission_param_sel_value_c
UNION ALL
 SELECT permission_param_sel_value_d.permission_parameter_desc_fk,
    permission_param_sel_value_d.description,
    permission_param_sel_value_d.value,
    0 AS weight
   FROM public.permission_param_sel_value_d;


--
-- Name: permission_parameter_type; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.permission_parameter_type (
    name character varying(30) NOT NULL
);


--
-- Name: TABLE permission_parameter_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.permission_parameter_type IS 'USER:40,5';


--
-- Name: presentation; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.presentation (
    id bigint NOT NULL,
    name character varying(255) NOT NULL,
    created_by_fk bigint,
    created_when timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
    scheduled_when timestamp without time zone,
    orgunit_fk bigint,
    patient_order bigint[] DEFAULT '{}'::bigint[] NOT NULL,
    patient_sorting public.patient_sort_mode DEFAULT 'alphabetical'::public.patient_sort_mode NOT NULL
);


--
-- Name: TABLE presentation; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.presentation IS 'PRES:0,10';


--
-- Name: presentation_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.presentation_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: product; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.product (
    id bigint NOT NULL,
    name character varying(250) NOT NULL,
    description character varying(2000) NOT NULL,
    CONSTRAINT pro_des_nn CHECK ((description IS NOT NULL)),
    CONSTRAINT pro_nam_nn CHECK ((name IS NOT NULL))
);


--
-- Name: TABLE product; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.product IS 'CONFIG:20,0';


--
-- Name: record_type; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.record_type (
    id smallint NOT NULL,
    name character varying(1000),
    index01_description character varying(1000),
    index01_type_hint_fk smallint,
    index02_description character varying(1000),
    index02_type_hint_fk smallint,
    index03_description character varying(1000),
    index03_type_hint_fk smallint,
    index04_description character varying(1000),
    index04_type_hint_fk smallint,
    index05_description character varying(1000),
    index05_type_hint_fk smallint,
    index06_description character varying(1000),
    index06_type_hint_fk smallint,
    index07_description character varying(1000),
    index07_type_hint_fk smallint,
    index08_description character varying(1000),
    index08_type_hint_fk smallint,
    index09_description character varying(1000),
    index09_type_hint_fk smallint,
    index10_description character varying(1000),
    index10_type_hint_fk smallint,
    index11_description character varying(1000),
    index11_type_hint_fk smallint,
    index12_description character varying(1000),
    index12_type_hint_fk smallint,
    index13_description character varying(1000),
    index13_type_hint_fk smallint,
    index14_description character varying(1000),
    index14_type_hint_fk smallint,
    index15_description character varying(1000),
    index15_type_hint_fk smallint,
    is_subtype boolean,
    CONSTRAINT rectyp_id_nn CHECK ((id IS NOT NULL))
);


--
-- Name: TABLE record_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.record_type IS 'DATA:30,50';


--
-- Name: scheduler_request; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.scheduler_request (
    id bigint NOT NULL,
    status character(1),
    error_msg character varying(1024),
    host character varying(512),
    application character varying(512),
    key1 character varying(512),
    val1 character varying(2048),
    key2 character varying(512),
    val2 character varying(512),
    key3 character varying(512),
    val3 character varying(512),
    key4 character varying(512),
    val4 character varying(512),
    key5 character varying(512),
    val5 character varying(512),
    inserted_when timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone,
    last_status_change timestamp without time zone,
    processing_host character varying(64),
    processing_pid bigint,
    processing_cmdline character varying(512),
    process_after timestamp without time zone,
    process_before timestamp without time zone,
    result character varying(1024),
    repeat_after bigint,
    CONSTRAINT scheduler_requests_inserted_nn CHECK ((inserted_when IS NOT NULL))
);


--
-- Name: TABLE scheduler_request; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.scheduler_request IS 'BACKEND:80,10';


--
-- Name: scheduler_request_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.scheduler_request_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: send_process; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.send_process (
    id bigint NOT NULL,
    description character varying(4096),
    state public.send_process_state NOT NULL,
    pacs_user_fk bigint,
    receiver character varying(1024),
    total bigint,
    processed bigint,
    started_when timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
    last_signal timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
    client_host character varying(255),
    client_application character varying(255)
);


--
-- Name: TABLE send_process; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.send_process IS 'BACKEND:70,20';


--
-- Name: send_process_message; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.send_process_message (
    id bigint NOT NULL,
    send_process_fk bigint NOT NULL,
    state public.send_process_state NOT NULL,
    code character varying(16) NOT NULL,
    message text,
    dicom_image_fk bigint,
    generic_file_fk bigint,
    created_when timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
    CONSTRAINT detail_fk_nn CHECK (((dicom_image_fk IS NOT NULL) OR (generic_file_fk IS NOT NULL)))
);


--
-- Name: TABLE send_process_message; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.send_process_message IS 'BACKEND:80,20';


--
-- Name: send_process_message_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.send_process_message_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: send_process_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.send_process_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: session_query_helper_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.session_query_helper_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 20;


--
-- Name: sex; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.sex (
    id character(1) NOT NULL,
    description character varying(200)
);


--
-- Name: TABLE sex; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.sex IS 'DATA:45,70';


--
-- Name: site_config; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.site_config (
    config_entry_base_fk bigint NOT NULL
);


--
-- Name: TABLE site_config; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.site_config IS 'CONFIG:0,10';


--
-- Name: snapshot; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.snapshot (
    id bigint NOT NULL,
    description character varying(2048) NOT NULL,
    content bytea,
    created_by_fk bigint,
    created_when timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
    type public.snapshot_type DEFAULT 'snapshot'::public.snapshot_type NOT NULL
);


--
-- Name: TABLE snapshot; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.snapshot IS 'PRES:0,0';


--
-- Name: snapshot_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.snapshot_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sop_class; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.sop_class (
    id smallint NOT NULL,
    uid character varying(255) NOT NULL,
    description character varying(255) NOT NULL
);


--
-- Name: TABLE sop_class; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.sop_class IS 'ORG:0,10';


--
-- Name: sop_class_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.sop_class_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: storage_commitment; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.storage_commitment (
    aet_fk bigint NOT NULL,
    dicom_image_fk bigint NOT NULL,
    committed_when timestamp without time zone,
    transaction_uid character varying(128)
);


--
-- Name: TABLE storage_commitment; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.storage_commitment IS 'DATA:48,40';


--
-- Name: storage_commitment_transaction_uid_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.storage_commitment_transaction_uid_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: storage_rule; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.storage_rule (
    id bigint NOT NULL,
    name character varying(64) NOT NULL,
    type public.storage_rule_type NOT NULL,
    active boolean DEFAULT false NOT NULL,
    orgunit_rule character varying(2048) NOT NULL,
    weight integer NOT NULL,
    tbl_compression_fk smallint,
    min_age bigint,
    max_age bigint,
    threshold_percent smallint,
    start_time time without time zone DEFAULT '00:00:00'::time without time zone NOT NULL,
    end_time time without time zone DEFAULT '24:00:00'::time without time zone NOT NULL,
    nr_threads smallint DEFAULT 1 NOT NULL,
    CONSTRAINT storage_rule_import_rule_has_compression CHECK (((tbl_compression_fk IS NOT NULL) OR (type <> 'import'::public.storage_rule_type))),
    CONSTRAINT storage_rule_name_not_empty CHECK (((name)::text <> ''::text)),
    CONSTRAINT storage_rule_nr_threads_is_positive CHECK ((nr_threads > 0)),
    CONSTRAINT storage_rule_orgunit_rule_not_empty CHECK (((orgunit_rule)::text <> ''::text))
);


--
-- Name: TABLE storage_rule; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.storage_rule IS 'ARCHIVE:0,0';


--
-- Name: storage_rule_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.storage_rule_seq
    START WITH 100
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: stored_query; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.stored_query (
    id bigint NOT NULL,
    project character varying(50) NOT NULL,
    version character varying(50) NOT NULL,
    query_name character varying(50) NOT NULL,
    statement character varying(4000) NOT NULL,
    maintainer character varying(255) NOT NULL,
    next_query bigint,
    CONSTRAINT stoque_dom_nn CHECK ((version IS NOT NULL)),
    CONSTRAINT stoque_mai_nn CHECK ((maintainer IS NOT NULL)),
    CONSTRAINT stoque_pro_nn CHECK ((project IS NOT NULL)),
    CONSTRAINT stoque_que_nn CHECK ((query_name IS NOT NULL)),
    CONSTRAINT stoque_sta_nn CHECK ((statement IS NOT NULL))
);


--
-- Name: TABLE stored_query; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.stored_query IS 'BACKEND:20,0';


--
-- Name: stored_query_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.stored_query_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 20;


--
-- Name: tbl_compression; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.tbl_compression (
    id smallint NOT NULL,
    method character varying(20) NOT NULL,
    extension character varying(20) NOT NULL,
    CONSTRAINT tblcomp_extension_nn CHECK ((extension IS NOT NULL)),
    CONSTRAINT tblcomp_id_nn CHECK ((id IS NOT NULL)),
    CONSTRAINT tblcomp_method_nn CHECK ((method IS NOT NULL))
);


--
-- Name: TABLE tbl_compression; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.tbl_compression IS 'ARCHIVE:15,5';


--
-- Name: tbl_container; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.tbl_container (
    id bigint NOT NULL,
    name character varying(256) NOT NULL,
    creation_date timestamp without time zone NOT NULL,
    CONSTRAINT tblcont_creation_nn CHECK ((creation_date IS NOT NULL)),
    CONSTRAINT tblcont_id_nn CHECK ((id IS NOT NULL)),
    CONSTRAINT tblcont_name_nn CHECK ((name IS NOT NULL))
);


--
-- Name: TABLE tbl_container; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.tbl_container IS 'ARCHIVE:10,7';


--
-- Name: tbl_container_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.tbl_container_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 20;


--
-- Name: tbl_instance; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.tbl_instance (
    id bigint NOT NULL,
    ismaster boolean NOT NULL,
    tbl_container_fk bigint NOT NULL,
    tbl_node_fk bigint NOT NULL,
    creation_date timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone,
    last_access_when timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
    CONSTRAINT tbl_instance_cd_nn CHECK ((creation_date IS NOT NULL)),
    CONSTRAINT tblinst_contfk_nn CHECK ((tbl_container_fk IS NOT NULL)),
    CONSTRAINT tblinst_id_nn CHECK ((id IS NOT NULL)),
    CONSTRAINT tblinst_ismaster_nn CHECK ((ismaster IS NOT NULL)),
    CONSTRAINT tblinst_nodefk_nn CHECK ((tbl_node_fk IS NOT NULL))
);


--
-- Name: TABLE tbl_instance; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.tbl_instance IS 'ARCHIVE:5,6';


--
-- Name: tbl_instance_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.tbl_instance_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 20;


--
-- Name: tbl_item; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.tbl_item (
    id bigint NOT NULL,
    name character varying(256) NOT NULL,
    isdeleted boolean NOT NULL,
    tbl_compression_fk smallint NOT NULL,
    tbl_container_fk bigint NOT NULL,
    CONSTRAINT tblitem_compression_nn CHECK ((tbl_compression_fk IS NOT NULL)),
    CONSTRAINT tblitem_contfk_nn CHECK ((tbl_container_fk IS NOT NULL)),
    CONSTRAINT tblitem_id_nn CHECK ((id IS NOT NULL)),
    CONSTRAINT tblitem_isdeleted_nn CHECK ((isdeleted IS NOT NULL)),
    CONSTRAINT tblitem_name_nn CHECK ((name IS NOT NULL))
);
ALTER TABLE ONLY public.tbl_item ALTER COLUMN isdeleted SET STATISTICS 10000;
ALTER TABLE ONLY public.tbl_item ALTER COLUMN tbl_container_fk SET STATISTICS 10000;


--
-- Name: TABLE tbl_item; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.tbl_item IS 'ARCHIVE:10,5';


--
-- Name: tbl_item_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.tbl_item_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 20;


--
-- Name: tbl_item_version; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.tbl_item_version (
    id bigint NOT NULL,
    version_number integer NOT NULL,
    byte_size_original bigint,
    byte_size bigint,
    md5_checksum_original character varying(32),
    md5_checksum character varying(32),
    isdeleted boolean NOT NULL,
    creation_date timestamp without time zone NOT NULL,
    tbl_item_fk bigint NOT NULL,
    is_archived boolean NOT NULL,
    is_verified boolean NOT NULL,
    archive boolean NOT NULL,
    metadata_size_original bigint,
    metadata_size bigint,
    CONSTRAINT tblitemver_archive_nn CHECK ((archive IS NOT NULL)),
    CONSTRAINT tblitemver_creation_nn CHECK ((creation_date IS NOT NULL)),
    CONSTRAINT tblitemver_id_nn CHECK ((id IS NOT NULL)),
    CONSTRAINT tblitemver_isarchived_nn CHECK ((is_archived IS NOT NULL)),
    CONSTRAINT tblitemver_isdeleted_nn CHECK ((isdeleted IS NOT NULL)),
    CONSTRAINT tblitemver_isverified_nn CHECK ((is_verified IS NOT NULL)),
    CONSTRAINT tblitemver_itemfk_nn CHECK ((tbl_item_fk IS NOT NULL)),
    CONSTRAINT tblitemver_version_nn CHECK ((version_number IS NOT NULL))
);


--
-- Name: TABLE tbl_item_version; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.tbl_item_version IS 'ARCHIVE:15,0';


--
-- Name: tbl_item_version_identifier; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.tbl_item_version_identifier (
    id bigint NOT NULL,
    tbl_item_version_fk bigint NOT NULL,
    identifier character varying(256) NOT NULL,
    CONSTRAINT tbl_item_version_identifier_identifier_format CHECK (((identifier)::text ~ similar_escape('[0-9a-z\-\_\.]*'::text, NULL::text)))
);


--
-- Name: TABLE tbl_item_version_identifier; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.tbl_item_version_identifier IS 'ARCHIVE:10,3';


--
-- Name: tbl_item_version_identifier_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.tbl_item_version_identifier_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: tbl_item_version_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.tbl_item_version_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 20;


--
-- Name: tbl_lock; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.tbl_lock (
    id bigint NOT NULL,
    kind character varying(1) NOT NULL,
    creation_date timestamp without time zone NOT NULL,
    tbl_container_fk bigint NOT NULL,
    host character varying(256),
    process_id bigint,
    thread_id bigint,
    project_name character varying(256),
    version_number character varying(256),
    CONSTRAINT tbllock_contfk_nn CHECK ((tbl_container_fk IS NOT NULL)),
    CONSTRAINT tbllock_creation_nn CHECK ((creation_date IS NOT NULL)),
    CONSTRAINT tbllock_id_nn CHECK ((id IS NOT NULL)),
    CONSTRAINT tbllock_kind_cc CHECK (((kind)::text = ANY (ARRAY[('E'::character varying)::text, ('S'::character varying)::text]))),
    CONSTRAINT tbllock_kind_nn CHECK ((kind IS NOT NULL))
);


--
-- Name: TABLE tbl_lock; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.tbl_lock IS 'ARCHIVE:15,7';


--
-- Name: tbl_lock_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.tbl_lock_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 20;


--
-- Name: tbl_lta_capacity_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.tbl_lta_capacity_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 20;


--
-- Name: tbl_lta_instance_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.tbl_lta_instance_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 20;


--
-- Name: tbl_lta_modality_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.tbl_lta_modality_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 20;


--
-- Name: tbl_lta_object_details_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.tbl_lta_object_details_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 20;


--
-- Name: tbl_lta_object_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.tbl_lta_object_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 20;


--
-- Name: tbl_node; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.tbl_node (
    id bigint NOT NULL,
    name character varying(256) NOT NULL,
    host character varying(256) NOT NULL,
    orbport bigint NOT NULL,
    rootdir character varying(256) NOT NULL,
    port bigint NOT NULL,
    CONSTRAINT tblnod_por_nn CHECK ((port IS NOT NULL)),
    CONSTRAINT tblnode_host_nn CHECK ((host IS NOT NULL)),
    CONSTRAINT tblnode_id_nn CHECK ((id IS NOT NULL)),
    CONSTRAINT tblnode_name_nn CHECK ((name IS NOT NULL)),
    CONSTRAINT tblnode_orbport_nn CHECK ((orbport IS NOT NULL)),
    CONSTRAINT tblnode_rootdir_nn CHECK ((rootdir IS NOT NULL))
);


--
-- Name: TABLE tbl_node; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.tbl_node IS 'ARCHIVE:0,7';


--
-- Name: tbl_node_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.tbl_node_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 20;


--
-- Name: transfer_syntax; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.transfer_syntax (
    id smallint NOT NULL,
    uid character varying(255) NOT NULL,
    description character varying(255) NOT NULL
);


--
-- Name: TABLE transfer_syntax; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.transfer_syntax IS 'ORG:0,5';


--
-- Name: transfer_syntax_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.transfer_syntax_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: treatment; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.treatment (
    id bigint NOT NULL,
    patient_fk bigint NOT NULL,
    orgunit_fk bigint NOT NULL,
    active boolean DEFAULT false NOT NULL
);


--
-- Name: TABLE treatment; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.treatment IS 'DATA:70,65';


--
-- Name: treatment_detail; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.treatment_detail (
    id bigint NOT NULL,
    treatment_fk bigint NOT NULL,
    source_type character varying(64) NOT NULL,
    source_id character varying(1024) NOT NULL,
    start_time timestamp without time zone NOT NULL,
    end_time timestamp without time zone NOT NULL,
    source_subid character varying(1024) DEFAULT ''::character varying NOT NULL
);


--
-- Name: TABLE treatment_detail; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.treatment_detail IS 'DATA:70,67';


--
-- Name: treatment_detail_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.treatment_detail_seq
    START WITH 100
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: treatment_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.treatment_seq
    START WITH 100
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: type_hint; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.type_hint (
    id smallint NOT NULL,
    name character varying(1000),
    description character varying(1000),
    CONSTRAINT typehint_id_nn CHECK ((id IS NOT NULL))
);


--
-- Name: TABLE type_hint; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.type_hint IS 'DATA:25,50';


--
-- Name: type_hint_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.type_hint_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 20;


--
-- Name: uid_chain; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.uid_chain (
    old_uid character varying(256) NOT NULL,
    new_uid character varying(256) NOT NULL,
    type public.uid_chain_type NOT NULL
);


--
-- Name: TABLE uid_chain; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.uid_chain IS 'ARCHIVE:5,14';


--
-- Name: unique_file_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.unique_file_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: user_config; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.user_config (
    pacs_user_fk bigint NOT NULL,
    update_mode character(1),
    config_entry_base_fk bigint NOT NULL,
    CONSTRAINT pacs_user_fk_nn CHECK ((pacs_user_fk IS NOT NULL)),
    CONSTRAINT user_config_cfg_entry_base_nn CHECK ((config_entry_base_fk IS NOT NULL))
);


--
-- Name: TABLE user_config; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.user_config IS 'CONFIG:20,10';


--
-- Name: view_by_pid; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE UNLOGGED TABLE public.view_by_pid (
    id bigint NOT NULL,
    pacs_session_fk bigint NOT NULL,
    patient_id character varying(1000) NOT NULL
);


--
-- Name: TABLE view_by_pid; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.view_by_pid IS 'USER:21,5';


--
-- Name: view_by_pid_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.view_by_pid_seq
    START WITH 1000
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: visit; Type: TABLE; Schema: public; Owner: -; Tablespace: syn_tbl
--

CREATE TABLE public.visit (
    id bigint NOT NULL,
    patient_class_fk character varying(2),
    assigned_patient_location character varying(80),
    preadmit_number character varying(32) NOT NULL,
    prior_patient_location character varying(80),
    attending_doctor character varying(150),
    referring_doctor character varying(150),
    temporary_location character varying(80),
    vip_indicator_fk character(2) DEFAULT '-'::bpchar,
    admitting_doctor character varying(150),
    discharge_disposition_fk character(3),
    discharged_to_location character varying(25),
    servicing_facility character varying(80),
    admit_date_time timestamp without time zone,
    discharge_date_time timestamp without time zone,
    alternate_visit_id character varying(20),
    visit_id bigint,
    inserted_when timestamp without time zone NOT NULL,
    inserted_by_fk smallint NOT NULL,
    last_modified_when timestamp without time zone NOT NULL,
    last_modified_by_fk smallint NOT NULL,
    deleted_when timestamp without time zone,
    deleted_by_fk smallint,
    status_deleted smallint NOT NULL,
    visit_invalidated_by_fk bigint,
    CONSTRAINT visit_ins_by_fk_nn CHECK ((inserted_by_fk IS NOT NULL)),
    CONSTRAINT visit_ins_when_fk_nn CHECK ((inserted_when IS NOT NULL)),
    CONSTRAINT visit_last_mod_by_fk_nn CHECK ((last_modified_by_fk IS NOT NULL)),
    CONSTRAINT visit_last_mod_when_fk_nn CHECK ((last_modified_when IS NOT NULL)),
    CONSTRAINT visit_preadmit_number_nn CHECK ((preadmit_number IS NOT NULL)),
    CONSTRAINT visit_sdel_nn CHECK ((status_deleted IS NOT NULL)),
    CONSTRAINT visit_vip_ind_nn CHECK ((vip_indicator_fk IS NOT NULL))
);


--
-- Name: TABLE visit; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.visit IS 'DATA:60,70';


--
-- Name: visit_marker_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.visit_marker_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: visit_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.visit_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


-- SET default_tablespace = syn_idx;

--
-- Name: aet aet_name_uq; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.aet
    ADD CONSTRAINT aet_name_uq UNIQUE (name);


--
-- Name: aet aet_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.aet
    ADD CONSTRAINT aet_pkey PRIMARY KEY (id);


--
-- Name: aim_conversion_interface aim_conversion_interface_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.aim_conversion_interface
    ADD CONSTRAINT aim_conversion_interface_pkey PRIMARY KEY (id);


--
-- Name: aim_conversion_interface aimconint_multi_uq; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.aim_conversion_interface
    ADD CONSTRAINT aimconint_multi_uq UNIQUE (identifier, subsystem, disabled, key, direction);


--
-- Name: annotation annotation_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.annotation
    ADD CONSTRAINT annotation_pkey PRIMARY KEY (id);


--
-- Name: ar_archive_object ar_archive_object_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.ar_archive_object
    ADD CONSTRAINT ar_archive_object_pkey PRIMARY KEY (id);


--
-- Name: ar_file ar_file_name_path_runway_uq; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.ar_file
    ADD CONSTRAINT ar_file_name_path_runway_uq UNIQUE (name, path, runway_name);


--
-- Name: ar_file ar_file_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.ar_file
    ADD CONSTRAINT ar_file_pkey PRIMARY KEY (id);


--
-- Name: ar_link_archive_object_file ar_link_archive_object_file_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.ar_link_archive_object_file
    ADD CONSTRAINT ar_link_archive_object_file_pkey PRIMARY KEY (ar_archive_object_fk, ar_file_fk);


--
-- Name: ar_file_summary ars_runway_name_uq; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.ar_file_summary
    ADD CONSTRAINT ars_runway_name_uq UNIQUE (runway_name);


--
-- Name: audit_event audit_event_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.audit_event
    ADD CONSTRAINT audit_event_pkey PRIMARY KEY (id);


--
-- Name: audit_event_property_key audit_event_property_key_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.audit_event_property_key
    ADD CONSTRAINT audit_event_property_key_pkey PRIMARY KEY (id);


--
-- Name: audit_event_property audit_event_property_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.audit_event_property
    ADD CONSTRAINT audit_event_property_pkey PRIMARY KEY (id);


--
-- Name: audit_record audit_record_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.audit_record
    ADD CONSTRAINT audit_record_pkey PRIMARY KEY (id);


--
-- Name: audit_record_source audit_record_source_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.audit_record_source
    ADD CONSTRAINT audit_record_source_pkey PRIMARY KEY (audit_record_fk);


--
-- Name: config_entry_description cfg_entry_desc_sec_nam_desc_uq; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.config_entry_description
    ADD CONSTRAINT cfg_entry_desc_sec_nam_desc_uq UNIQUE (config_section_description_fk, name, description);


--
-- Name: config_section_description cfg_sec_desc_name_desc_uq; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.config_section_description
    ADD CONSTRAINT cfg_sec_desc_name_desc_uq UNIQUE (name, description);


--
-- Name: characterset characterset_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.characterset
    ADD CONSTRAINT characterset_pkey PRIMARY KEY (name);


--
-- Name: config_entry_base config_entry_base_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.config_entry_base
    ADD CONSTRAINT config_entry_base_pkey PRIMARY KEY (id);


--
-- Name: config_entry_description config_entry_description_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.config_entry_description
    ADD CONSTRAINT config_entry_description_pkey PRIMARY KEY (id);


--
-- Name: config_section_description config_section_description_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.config_section_description
    ADD CONSTRAINT config_section_description_pkey PRIMARY KEY (id);


--
-- Name: default_config default_config_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.default_config
    ADD CONSTRAINT default_config_pkey PRIMARY KEY (config_entry_base_fk);


--
-- Name: diagnostic_report diagnostic_report_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.diagnostic_report
    ADD CONSTRAINT diagnostic_report_pkey PRIMARY KEY (id);


--
-- Name: dicom_image dicom_image_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.dicom_image
    ADD CONSTRAINT dicom_image_pkey PRIMARY KEY (id);


--
-- Name: dicom_mail_recipient dicom_mail_recipient_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.dicom_mail_recipient
    ADD CONSTRAINT dicom_mail_recipient_pkey PRIMARY KEY (id);


--
-- Name: dicom_series dicom_series_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.dicom_series
    ADD CONSTRAINT dicom_series_pkey PRIMARY KEY (id);


--
-- Name: dicom_study dicom_study_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.dicom_study
    ADD CONSTRAINT dicom_study_pkey PRIMARY KEY (document_fk);


--
-- Name: discharge_disposition discharge_disposition_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.discharge_disposition
    ADD CONSTRAINT discharge_disposition_pkey PRIMARY KEY (id);


--
-- Name: document_export docexp_unidocfk_uq; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.document_export
    ADD CONSTRAINT docexp_unidocfk_uq UNIQUE (unique_document_fk);


--
-- Name: document_class_display document_class_display_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.document_class_display
    ADD CONSTRAINT document_class_display_pkey PRIMARY KEY (id);


--
-- Name: document_class_group document_class_group_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.document_class_group
    ADD CONSTRAINT document_class_group_pkey PRIMARY KEY (id);


--
-- Name: document_class document_class_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.document_class
    ADD CONSTRAINT document_class_pkey PRIMARY KEY (id);


--
-- Name: document_export document_export_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.document_export
    ADD CONSTRAINT document_export_pkey PRIMARY KEY (id);


--
-- Name: document_import_helper document_import_helper_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.document_import_helper
    ADD CONSTRAINT document_import_helper_pkey PRIMARY KEY (document_fk);


--
-- Name: document_marker document_marker_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.document_marker
    ADD CONSTRAINT document_marker_pkey PRIMARY KEY (id);


--
-- Name: document document_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.document
    ADD CONSTRAINT document_pkey PRIMARY KEY (id);


--
-- Name: document_share document_share_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.document_share
    ADD CONSTRAINT document_share_pkey PRIMARY KEY (id);


--
-- Name: document_type document_type_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.document_type
    ADD CONSTRAINT document_type_pkey PRIMARY KEY (id);


--
-- Name: extension_group extension_group_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.extension_group
    ADD CONSTRAINT extension_group_pkey PRIMARY KEY (name);


--
-- Name: extension extension_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.extension
    ADD CONSTRAINT extension_pkey PRIMARY KEY (abbreviation);


--
-- Name: fhir_identifier fhir_identifier_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.fhir_identifier
    ADD CONSTRAINT fhir_identifier_pkey PRIMARY KEY (id);


--
-- Name: fsnode_archive_object fsnode_archive_object_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.fsnode_archive_object
    ADD CONSTRAINT fsnode_archive_object_pkey PRIMARY KEY (id);


--
-- Name: fsnode_injected_file fsnode_injected_file_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.fsnode_injected_file
    ADD CONSTRAINT fsnode_injected_file_pkey PRIMARY KEY (id);


--
-- Name: generic_container generic_container_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.generic_container
    ADD CONSTRAINT generic_container_pkey PRIMARY KEY (document_fk);


--
-- Name: generic_file generic_file_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.generic_file
    ADD CONSTRAINT generic_file_pkey PRIMARY KEY (id);


--
-- Name: hl7_notification hl7_notification_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.hl7_notification
    ADD CONSTRAINT hl7_notification_pkey PRIMARY KEY (id);


--
-- Name: hl7_notification_status hl7_notification_status_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.hl7_notification_status
    ADD CONSTRAINT hl7_notification_status_pkey PRIMARY KEY (id);


--
-- Name: host_config host_config_host_mode_cfg_uq; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.host_config
    ADD CONSTRAINT host_config_host_mode_cfg_uq UNIQUE (host_fk, update_mode, config_entry_base_fk);


--
-- Name: host_config host_config_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.host_config
    ADD CONSTRAINT host_config_pkey PRIMARY KEY (config_entry_base_fk);


--
-- Name: host host_name_uq; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.host
    ADD CONSTRAINT host_name_uq UNIQUE (name);


--
-- Name: host host_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.host
    ADD CONSTRAINT host_pkey PRIMARY KEY (id);


--
-- Name: ihe_actor ihe_actor_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.ihe_actor
    ADD CONSTRAINT ihe_actor_pkey PRIMARY KEY (id);


--
-- Name: ihe_endpoint ihe_endpoint_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.ihe_endpoint
    ADD CONSTRAINT ihe_endpoint_pkey PRIMARY KEY (ihe_actor_fk, transaction);


--
-- Name: ihe_manifest ihe_manifest_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.ihe_manifest
    ADD CONSTRAINT ihe_manifest_pkey PRIMARY KEY (id);


--
-- Name: image_marker image_marker_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.image_marker
    ADD CONSTRAINT image_marker_pkey PRIMARY KEY (id);


--
-- Name: imedone_manifest imedone_manifest_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.imedone_manifest
    ADD CONSTRAINT imedone_manifest_pkey PRIMARY KEY (id);


--
-- Name: iocm_rejects iocm_rejects_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.iocm_rejects
    ADD CONSTRAINT iocm_rejects_pkey PRIMARY KEY (sop_instance_uid);


--
-- Name: item_version_security item_version_security_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.item_version_security
    ADD CONSTRAINT item_version_security_pkey PRIMARY KEY (id);


--
-- Name: item_version_security_prop item_version_security_prop_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.item_version_security_prop
    ADD CONSTRAINT item_version_security_prop_pkey PRIMARY KEY (id);


--
-- Name: keyword_class_constraint keyword_class_constraint_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.keyword_class_constraint
    ADD CONSTRAINT keyword_class_constraint_pkey PRIMARY KEY (keyword_class_fk, keyword_class_const_type_fk);


--
-- Name: keyword_class_constraint_type keyword_class_constraint_type_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.keyword_class_constraint_type
    ADD CONSTRAINT keyword_class_constraint_type_pkey PRIMARY KEY (name);


--
-- Name: keyword_class_group keyword_class_group_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.keyword_class_group
    ADD CONSTRAINT keyword_class_group_pkey PRIMARY KEY (name);


--
-- Name: keyword_class keyword_class_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.keyword_class
    ADD CONSTRAINT keyword_class_pkey PRIMARY KEY (id);


--
-- Name: keyword_class_type keyword_class_type_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.keyword_class_type
    ADD CONSTRAINT keyword_class_type_pkey PRIMARY KEY (id);


--
-- Name: keyword_display keyword_display_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.keyword_display
    ADD CONSTRAINT keyword_display_pkey PRIMARY KEY (id);


--
-- Name: keyword_level keyword_level_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.keyword_level
    ADD CONSTRAINT keyword_level_pkey PRIMARY KEY (name);


--
-- Name: keyword keyword_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.keyword
    ADD CONSTRAINT keyword_pkey PRIMARY KEY (id);


--
-- Name: keyword_class kw_class_name_uq; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.keyword_class
    ADD CONSTRAINT kw_class_name_uq UNIQUE (name);


--
-- Name: language language_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.language
    ADD CONSTRAINT language_pkey PRIMARY KEY (id);


--
-- Name: link_aet_aet link_aet_aet_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.link_aet_aet
    ADD CONSTRAINT link_aet_aet_pkey PRIMARY KEY (client_aet_fk, modgrp_aet_fk);


--
-- Name: link_aet_host link_aet_host_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.link_aet_host
    ADD CONSTRAINT link_aet_host_pkey PRIMARY KEY (aet_fk, host_fk);


--
-- Name: link_aet_ignored_sop_class link_aet_ignored_sop_class_pk; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.link_aet_ignored_sop_class
    ADD CONSTRAINT link_aet_ignored_sop_class_pk PRIMARY KEY (aet_fk, sop_class_fk);


--
-- Name: link_aet_transfer_syntax link_aet_transfer_syntax_pk; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.link_aet_transfer_syntax
    ADD CONSTRAINT link_aet_transfer_syntax_pk PRIMARY KEY (aet_fk, transfer_syntax_fk);


--
-- Name: link_diagnostic_report_fhir_identifier link_diagnostic_report_fhir_identifier_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.link_diagnostic_report_fhir_identifier
    ADD CONSTRAINT link_diagnostic_report_fhir_identifier_pkey PRIMARY KEY (diagnostic_report_fk, fhir_identifier_fk);


--
-- Name: link_dicom_image_image_marker link_dicom_image_image_marker_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.link_dicom_image_image_marker
    ADD CONSTRAINT link_dicom_image_image_marker_pkey PRIMARY KEY (dicom_image_fk, image_marker_fk);


--
-- Name: link_dicom_image_keyword link_dicom_image_keyword_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.link_dicom_image_keyword
    ADD CONSTRAINT link_dicom_image_keyword_pkey PRIMARY KEY (dicom_image_fk, keyword_fk);


--
-- Name: link_dicom_series_keyword link_dicom_series_keyword_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.link_dicom_series_keyword
    ADD CONSTRAINT link_dicom_series_keyword_pkey PRIMARY KEY (dicom_series_fk, keyword_fk);


--
-- Name: link_document_class_group link_document_class_group_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.link_document_class_group
    ADD CONSTRAINT link_document_class_group_pkey PRIMARY KEY (document_class_fk, document_class_group_fk);


--
-- Name: link_document_class_keyword link_document_class_keyword_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.link_document_class_keyword
    ADD CONSTRAINT link_document_class_keyword_pkey PRIMARY KEY (document_class_fk, keyword_fk);


--
-- Name: link_document_document_marker link_document_document_marker_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.link_document_document_marker
    ADD CONSTRAINT link_document_document_marker_pkey PRIMARY KEY (document_fk, document_marker_fk);


--
-- Name: link_document_keyword_all link_document_keyword_all_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.link_document_keyword_all
    ADD CONSTRAINT link_document_keyword_all_pkey PRIMARY KEY (document_fk, keyword_fk);


--
-- Name: link_document_keyword link_document_keyword_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.link_document_keyword
    ADD CONSTRAINT link_document_keyword_pkey PRIMARY KEY (document_fk, keyword_fk);


--
-- Name: link_extension_extension_group link_extension_extension_group_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.link_extension_extension_group
    ADD CONSTRAINT link_extension_extension_group_pkey PRIMARY KEY (extension_fk, extension_group_fk);


--
-- Name: link_generic_file_image_marker link_generic_file_image_marker_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.link_generic_file_image_marker
    ADD CONSTRAINT link_generic_file_image_marker_pkey PRIMARY KEY (generic_file_fk, image_marker_fk);


--
-- Name: link_generic_file_keyword link_generic_file_keyword_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.link_generic_file_keyword
    ADD CONSTRAINT link_generic_file_keyword_pkey PRIMARY KEY (generic_file_fk, keyword_fk);


--
-- Name: link_medication_administration_fhir_identifier link_medication_administration_fhir_identifier_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.link_medication_administration_fhir_identifier
    ADD CONSTRAINT link_medication_administration_fhir_identifier_pkey PRIMARY KEY (medication_administration_fk, fhir_identifier_fk);


--
-- Name: link_o_procedure_keyword link_o_procedure_keyword_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.link_o_procedure_keyword
    ADD CONSTRAINT link_o_procedure_keyword_pkey PRIMARY KEY (o_procedure_fk, keyword_fk);


--
-- Name: link_observation_fhir_identifier link_observation_fhir_identifier_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.link_observation_fhir_identifier
    ADD CONSTRAINT link_observation_fhir_identifier_pkey PRIMARY KEY (observation_fk, fhir_identifier_fk);


--
-- Name: link_patient_visit link_patient_visit_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.link_patient_visit
    ADD CONSTRAINT link_patient_visit_pkey PRIMARY KEY (id);


--
-- Name: link_presentation_document link_presentation_document_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.link_presentation_document
    ADD CONSTRAINT link_presentation_document_pkey PRIMARY KEY (presentation_fk, document_fk);


--
-- Name: link_report_item_version_security link_report_item_version_security_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.link_report_item_version_security
    ADD CONSTRAINT link_report_item_version_security_pkey PRIMARY KEY (item_version_security_fk);


--
-- Name: link_role_pacs_user link_role_pacs_user_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.link_role_pacs_user
    ADD CONSTRAINT link_role_pacs_user_pkey PRIMARY KEY (role_fk, pacs_user_fk);


--
-- Name: link_role_permission link_role_permission_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.link_role_permission
    ADD CONSTRAINT link_role_permission_pkey PRIMARY KEY (id);


--
-- Name: link_snapshot_document link_snapshot_document_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.link_snapshot_document
    ADD CONSTRAINT link_snapshot_document_pkey PRIMARY KEY (snapshot_fk, document_fk);


--
-- Name: link_storage_rule_tbl_node link_storage_rule_tbl_node_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.link_storage_rule_tbl_node
    ADD CONSTRAINT link_storage_rule_tbl_node_pkey PRIMARY KEY (tbl_node_fk, storage_rule_fk, node_type);


--
-- Name: link_tbl_node_tbl_item_version_fsnode_archive_object link_tbl_node_tbl_item_version_fsnode_archive_object_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.link_tbl_node_tbl_item_version_fsnode_archive_object
    ADD CONSTRAINT link_tbl_node_tbl_item_version_fsnode_archive_object_pkey PRIMARY KEY (tbl_node_fk, tbl_item_version_fk);


--
-- Name: link_patient_visit lpv_pat_vis_uq; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.link_patient_visit
    ADD CONSTRAINT lpv_pat_vis_uq UNIQUE (patient_fk, visit_fk);


--
-- Name: link_patient_visit lpv_visit_uq; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.link_patient_visit
    ADD CONSTRAINT lpv_visit_uq UNIQUE (visit_fk);


--
-- Name: medication_administration medication_administration_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.medication_administration
    ADD CONSTRAINT medication_administration_pkey PRIMARY KEY (id);


--
-- Name: modifier mod_priority_uq; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.modifier
    ADD CONSTRAINT mod_priority_uq UNIQUE (priority);


--
-- Name: modality modality_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.modality
    ADD CONSTRAINT modality_pkey PRIMARY KEY (id);


--
-- Name: modifier modifier_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.modifier
    ADD CONSTRAINT modifier_pkey PRIMARY KEY (id);


--
-- Name: mpps_image_info mpps_image_info_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.mpps_image_info
    ADD CONSTRAINT mpps_image_info_pkey PRIMARY KEY (sop_instance_uid);


--
-- Name: mpps_info mpps_info_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.mpps_info
    ADD CONSTRAINT mpps_info_pkey PRIMARY KEY (id);


--
-- Name: mpps_series_info mpps_series_info_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.mpps_series_info
    ADD CONSTRAINT mpps_series_info_pkey PRIMARY KEY (id);


--
-- Name: node_info node_info_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.node_info
    ADD CONSTRAINT node_info_pkey PRIMARY KEY (tbl_node_fk);


--
-- Name: notification_event notification_event_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.notification_event
    ADD CONSTRAINT notification_event_pkey PRIMARY KEY (id);


--
-- Name: notification notification_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.notification
    ADD CONSTRAINT notification_pkey PRIMARY KEY (id);


--
-- Name: notification_type notification_type_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.notification_type
    ADD CONSTRAINT notification_type_pkey PRIMARY KEY (id);


--
-- Name: o_procedure o_procedure_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.o_procedure
    ADD CONSTRAINT o_procedure_pkey PRIMARY KEY (id);


--
-- Name: observation observation_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.observation
    ADD CONSTRAINT observation_pkey PRIMARY KEY (id);


--
-- Name: order_additional_info order_additional_info_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.order_additional_info
    ADD CONSTRAINT order_additional_info_pkey PRIMARY KEY (order_entry_fk, key);


--
-- Name: order_entry order_e_accnum_uq; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.order_entry
    ADD CONSTRAINT order_e_accnum_uq UNIQUE (accession_number);


--
-- Name: order_entry order_e_series_uq; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.order_entry
    ADD CONSTRAINT order_e_series_uq UNIQUE (series_instance_uid);


--
-- Name: order_entry order_e_study_uq; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.order_entry
    ADD CONSTRAINT order_e_study_uq UNIQUE (study_instance_uid);


--
-- Name: order_entry order_entry_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.order_entry
    ADD CONSTRAINT order_entry_pkey PRIMARY KEY (id);


--
-- Name: order_root order_r_dom_extid_uq; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.order_root
    ADD CONSTRAINT order_r_dom_extid_uq UNIQUE (domain, external_id);


--
-- Name: order_root order_root_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.order_root
    ADD CONSTRAINT order_root_pkey PRIMARY KEY (order_entry_fk);


--
-- Name: order_status order_status_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.order_status
    ADD CONSTRAINT order_status_pkey PRIMARY KEY (code);


--
-- Name: orderer orderer_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.orderer
    ADD CONSTRAINT orderer_pkey PRIMARY KEY (order_entry_fk, abbrevation, type_fk);


--
-- Name: orderer_type orderer_type_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.orderer_type
    ADD CONSTRAINT orderer_type_pkey PRIMARY KEY (name);


--
-- Name: orgunit orgu_abk_uq; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.orgunit
    ADD CONSTRAINT orgu_abk_uq UNIQUE (abk);


--
-- Name: orgunit_config orgunit_cfg_org_cfg_entry_uq; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.orgunit_config
    ADD CONSTRAINT orgunit_cfg_org_cfg_entry_uq UNIQUE (orgunit_fk, config_entry_base_fk);


--
-- Name: orgunit_config orgunit_config_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.orgunit_config
    ADD CONSTRAINT orgunit_config_pkey PRIMARY KEY (config_entry_base_fk);


--
-- Name: orgunit orgunit_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.orgunit
    ADD CONSTRAINT orgunit_pkey PRIMARY KEY (id);


--
-- Name: pacs_session_parameter pacs_session_parameter_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.pacs_session_parameter
    ADD CONSTRAINT pacs_session_parameter_pkey PRIMARY KEY (pacs_session_permission_fk, parameter);


--
-- Name: pacs_session_permission pacs_session_permission_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.pacs_session_permission
    ADD CONSTRAINT pacs_session_permission_pkey PRIMARY KEY (id);


--
-- Name: pacs_session pacs_session_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.pacs_session
    ADD CONSTRAINT pacs_session_pkey PRIMARY KEY (id);


--
-- Name: pacs_user_attribute pacs_user_attribute_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.pacs_user_attribute
    ADD CONSTRAINT pacs_user_attribute_pkey PRIMARY KEY (id);


--
-- Name: pacs_user pacs_user_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.pacs_user
    ADD CONSTRAINT pacs_user_pkey PRIMARY KEY (id);


--
-- Name: pacs_user_token pacs_user_token_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.pacs_user_token
    ADD CONSTRAINT pacs_user_token_pkey PRIMARY KEY (id);


--
-- Name: parameter parameter_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.parameter
    ADD CONSTRAINT parameter_pkey PRIMARY KEY (link_role_permission_fk, permission_parameter_desc_fk);


--
-- Name: patient_class patient_class_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.patient_class
    ADD CONSTRAINT patient_class_pkey PRIMARY KEY (id);


--
-- Name: patient patient_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.patient
    ADD CONSTRAINT patient_pkey PRIMARY KEY (id);


--
-- Name: permission_parameter_desc perm_param_desc_perm_name_uq; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.permission_parameter_desc
    ADD CONSTRAINT perm_param_desc_perm_name_uq UNIQUE (permission_fk, name);


--
-- Name: permission permission_name_uq; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.permission
    ADD CONSTRAINT permission_name_uq UNIQUE (name);


--
-- Name: permission_parameter_desc permission_parameter_desc_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.permission_parameter_desc
    ADD CONSTRAINT permission_parameter_desc_pkey PRIMARY KEY (id);


--
-- Name: permission_parameter_type permission_parameter_type_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.permission_parameter_type
    ADD CONSTRAINT permission_parameter_type_pkey PRIMARY KEY (name);


--
-- Name: permission permission_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.permission
    ADD CONSTRAINT permission_pkey PRIMARY KEY (id);


--
-- Name: presentation presentation_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.presentation
    ADD CONSTRAINT presentation_pkey PRIMARY KEY (id);


--
-- Name: product pro_nam_uq; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.product
    ADD CONSTRAINT pro_nam_uq UNIQUE (name);


--
-- Name: product product_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.product
    ADD CONSTRAINT product_pkey PRIMARY KEY (id);


--
-- Name: pacs_user puser_employeeid_uq; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.pacs_user
    ADD CONSTRAINT puser_employeeid_uq UNIQUE (employeeid);


--
-- Name: pacs_user puser_login_uq; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.pacs_user
    ADD CONSTRAINT puser_login_uq UNIQUE (login);


--
-- Name: record_type record_type_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.record_type
    ADD CONSTRAINT record_type_pkey PRIMARY KEY (id);


--
-- Name: record_type rectyp_name_uq; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.record_type
    ADD CONSTRAINT rectyp_name_uq UNIQUE (name);


--
-- Name: role role_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.role
    ADD CONSTRAINT role_pkey PRIMARY KEY (name);


--
-- Name: scheduler_request scheduler_request_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.scheduler_request
    ADD CONSTRAINT scheduler_request_pkey PRIMARY KEY (id);


--
-- Name: send_process_message send_process_message_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.send_process_message
    ADD CONSTRAINT send_process_message_pkey PRIMARY KEY (id);


--
-- Name: send_process send_process_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.send_process
    ADD CONSTRAINT send_process_pkey PRIMARY KEY (id);


--
-- Name: sex sex_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.sex
    ADD CONSTRAINT sex_pkey PRIMARY KEY (id);


--
-- Name: site_config site_config_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.site_config
    ADD CONSTRAINT site_config_pkey PRIMARY KEY (config_entry_base_fk);


--
-- Name: snapshot snapshot_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.snapshot
    ADD CONSTRAINT snapshot_pkey PRIMARY KEY (id);


--
-- Name: sop_class sop_class_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.sop_class
    ADD CONSTRAINT sop_class_pkey PRIMARY KEY (id);


--
-- Name: sop_class sop_class_uid_uq; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.sop_class
    ADD CONSTRAINT sop_class_uid_uq UNIQUE (uid);


--
-- Name: stored_query stoque_pro_dom_que_uq; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.stored_query
    ADD CONSTRAINT stoque_pro_dom_que_uq UNIQUE (project, version, query_name);


--
-- Name: storage_commitment storage_commitment_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.storage_commitment
    ADD CONSTRAINT storage_commitment_pkey PRIMARY KEY (aet_fk, dicom_image_fk);


--
-- Name: storage_rule storage_rule_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.storage_rule
    ADD CONSTRAINT storage_rule_pkey PRIMARY KEY (id);


--
-- Name: stored_query stored_query_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.stored_query
    ADD CONSTRAINT stored_query_pkey PRIMARY KEY (id);


--
-- Name: tbl_compression tbl_compression_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.tbl_compression
    ADD CONSTRAINT tbl_compression_pkey PRIMARY KEY (id);


--
-- Name: tbl_container tbl_container_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.tbl_container
    ADD CONSTRAINT tbl_container_pkey PRIMARY KEY (id);


--
-- Name: tbl_instance tbl_instance_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.tbl_instance
    ADD CONSTRAINT tbl_instance_pkey PRIMARY KEY (id);


--
-- Name: tbl_item tbl_item_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.tbl_item
    ADD CONSTRAINT tbl_item_pkey PRIMARY KEY (id);


--
-- Name: tbl_item_version_identifier tbl_item_version_identifier_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.tbl_item_version_identifier
    ADD CONSTRAINT tbl_item_version_identifier_pkey PRIMARY KEY (id);


--
-- Name: tbl_item_version tbl_item_version_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.tbl_item_version
    ADD CONSTRAINT tbl_item_version_pkey PRIMARY KEY (id);


--
-- Name: tbl_lock tbl_lock_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.tbl_lock
    ADD CONSTRAINT tbl_lock_pkey PRIMARY KEY (id);


--
-- Name: tbl_node tbl_node_hpp_uq; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.tbl_node
    ADD CONSTRAINT tbl_node_hpp_uq UNIQUE (host, port, rootdir);


--
-- Name: tbl_node tbl_node_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.tbl_node
    ADD CONSTRAINT tbl_node_pkey PRIMARY KEY (id);


--
-- Name: tbl_compression tblcomp_extension_uq; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.tbl_compression
    ADD CONSTRAINT tblcomp_extension_uq UNIQUE (extension);


--
-- Name: tbl_compression tblcomp_method_uq; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.tbl_compression
    ADD CONSTRAINT tblcomp_method_uq UNIQUE (method);


--
-- Name: tbl_container tblcont_name_uq; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.tbl_container
    ADD CONSTRAINT tblcont_name_uq UNIQUE (name);


--
-- Name: tbl_item tblitem_namecontainerfk_uq; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.tbl_item
    ADD CONSTRAINT tblitem_namecontainerfk_uq UNIQUE (name, tbl_container_fk);


--
-- Name: tbl_item_version tbliv_itemfk_version_uq; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.tbl_item_version
    ADD CONSTRAINT tbliv_itemfk_version_uq UNIQUE (tbl_item_fk, version_number);


--
-- Name: tbl_node tblnode_name_uq; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.tbl_node
    ADD CONSTRAINT tblnode_name_uq UNIQUE (name);


--
-- Name: transfer_syntax transfer_syntax_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.transfer_syntax
    ADD CONSTRAINT transfer_syntax_pkey PRIMARY KEY (id);


--
-- Name: transfer_syntax transfer_syntax_uid_uq; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.transfer_syntax
    ADD CONSTRAINT transfer_syntax_uid_uq UNIQUE (uid);


--
-- Name: treatment_detail treatment_detail_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.treatment_detail
    ADD CONSTRAINT treatment_detail_pkey PRIMARY KEY (id);


--
-- Name: treatment treatment_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.treatment
    ADD CONSTRAINT treatment_pkey PRIMARY KEY (id);


--
-- Name: type_hint type_hint_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.type_hint
    ADD CONSTRAINT type_hint_pkey PRIMARY KEY (id);


--
-- Name: uid_chain uid_chain_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.uid_chain
    ADD CONSTRAINT uid_chain_pkey PRIMARY KEY (new_uid);


--
-- Name: user_config user_config_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.user_config
    ADD CONSTRAINT user_config_pkey PRIMARY KEY (config_entry_base_fk);


--
-- Name: user_config user_config_user_mode_cfg_uq; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.user_config
    ADD CONSTRAINT user_config_user_mode_cfg_uq UNIQUE (pacs_user_fk, update_mode, config_entry_base_fk);


--
-- Name: view_by_pid view_by_pid_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.view_by_pid
    ADD CONSTRAINT view_by_pid_pkey PRIMARY KEY (id);


--
-- Name: vip_indicator vip_indicator_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.vip_indicator
    ADD CONSTRAINT vip_indicator_pkey PRIMARY KEY (id);


--
-- Name: visit visit_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: syn_idx
--

ALTER TABLE ONLY public.visit
    ADD CONSTRAINT visit_pkey PRIMARY KEY (id);


--
-- Name: aep_audit_event_fk; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX aep_audit_event_fk ON public.audit_event_property USING btree (audit_event_fk);


--
-- Name: aep_key_fk_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX aep_key_fk_ix ON public.audit_event_property USING btree (key_fk);


--
-- Name: aep_value_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX aep_value_ix ON public.audit_event_property USING btree (value text_pattern_ops);


--
-- Name: aepk_key_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX aepk_key_ix ON public.audit_event_property_key USING btree (key text_pattern_ops);


--
-- Name: aet_name_uq2; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE UNIQUE INDEX aet_name_uq2 ON public.aet USING btree (name text_pattern_ops);


--
-- Name: aet_orgunit_fk; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX aet_orgunit_fk ON public.aet USING btree (orgunit_fk);


--
-- Name: annotation_dicom_image_fk_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX annotation_dicom_image_fk_ix ON public.annotation USING btree (dicom_image_fk) WHERE (dicom_image_fk IS NOT NULL);


--
-- Name: annotation_document_fk_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX annotation_document_fk_ix ON public.annotation USING btree (document_fk) WHERE (document_fk IS NOT NULL);


--
-- Name: annotation_generic_file_fk_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX annotation_generic_file_fk_ix ON public.annotation USING btree (generic_file_fk) WHERE (generic_file_fk IS NOT NULL);


--
-- Name: ar_archive_object_runame_migrated_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX ar_archive_object_runame_migrated_ix ON public.ar_archive_object USING btree (runway_name, migrated);


--
-- Name: ararchiveobject_status_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX ararchiveobject_status_ix ON public.ar_archive_object USING btree (status text_pattern_ops);


--
-- Name: ararob_runame_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX ararob_runame_ix ON public.ar_archive_object USING btree (runway_name text_pattern_ops);


--
-- Name: arfile_is_online_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX arfile_is_online_ix ON public.ar_file USING btree (is_online);


--
-- Name: arfile_lastacc_online; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX arfile_lastacc_online ON public.ar_file USING btree (runway_name, last_access_when) WHERE (is_online = true);


--
-- Name: arfile_name_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX arfile_name_ix ON public.ar_file USING btree (name text_pattern_ops);


--
-- Name: arfile_path_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX arfile_path_ix ON public.ar_file USING btree (path text_pattern_ops);


--
-- Name: arfile_path_reverse_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX arfile_path_reverse_ix ON public.ar_file USING btree (reverse((path)::text) text_pattern_ops);


--
-- Name: arfile_runame_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX arfile_runame_ix ON public.ar_file USING btree (runway_name text_pattern_ops);


--
-- Name: arfile_status_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX arfile_status_ix ON public.ar_file USING btree (status text_pattern_ops);


--
-- Name: arliarobarfi_arfi_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX arliarobarfi_arfi_ix ON public.ar_link_archive_object_file USING btree (ar_file_fk);


--
-- Name: arliarobarfi_arob_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX arliarobarfi_arob_ix ON public.ar_link_archive_object_file USING btree (ar_archive_object_fk);


--
-- Name: audit_event_time_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX audit_event_time_ix ON public.audit_event USING btree ("time");


--
-- Name: audit_event_type_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX audit_event_type_ix ON public.audit_event USING btree (type text_pattern_ops);


--
-- Name: audit_event_unmigrated_date_pix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX audit_event_unmigrated_date_pix ON public.audit_event USING btree ((("time")::date)) WHERE (migrated IS NULL);


--
-- Name: audit_record_affected_pacs_user_fk_pix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX audit_record_affected_pacs_user_fk_pix ON public.audit_record USING btree (affected_pacs_user_fk) WHERE (affected_pacs_user_fk IS NOT NULL);


--
-- Name: audit_record_audit_source_id_upper_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX audit_record_audit_source_id_upper_ix ON public.audit_record USING btree (upper((audit_source_id)::text) text_pattern_ops);


--
-- Name: audit_record_destination_alt_id_upper_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX audit_record_destination_alt_id_upper_ix ON public.audit_record USING btree (upper((destination_alt_id)::text) text_pattern_ops);


--
-- Name: audit_record_destination_id_upper_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX audit_record_destination_id_upper_ix ON public.audit_record USING btree (upper((destination_id)::text) text_pattern_ops);


--
-- Name: audit_record_destination_network_access_point_id_upper_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX audit_record_destination_network_access_point_id_upper_ix ON public.audit_record USING btree (upper((destination_network_access_point_id)::text) text_pattern_ops);


--
-- Name: audit_record_dicom_image_fk_pix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX audit_record_dicom_image_fk_pix ON public.audit_record USING btree (dicom_image_fk) WHERE (dicom_image_fk IS NOT NULL);


--
-- Name: audit_record_dicom_series_fk_pix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX audit_record_dicom_series_fk_pix ON public.audit_record USING btree (dicom_series_fk) WHERE (dicom_series_fk IS NOT NULL);


--
-- Name: audit_record_document_fk_pix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX audit_record_document_fk_pix ON public.audit_record USING btree (document_fk) WHERE (document_fk IS NOT NULL);


--
-- Name: audit_record_event_action_code_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX audit_record_event_action_code_ix ON public.audit_record USING btree (event_action_code);


--
-- Name: audit_record_event_date_time_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX audit_record_event_date_time_ix ON public.audit_record USING btree (event_date_time);


--
-- Name: audit_record_event_id_code_upper_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX audit_record_event_id_code_upper_ix ON public.audit_record USING btree (upper((event_id_code)::text) text_pattern_ops);


--
-- Name: audit_record_event_type_code_upper_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX audit_record_event_type_code_upper_ix ON public.audit_record USING btree (upper((event_type_code)::text) text_pattern_ops);


--
-- Name: audit_record_generic_file_fk_pix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX audit_record_generic_file_fk_pix ON public.audit_record USING btree (generic_file_fk) WHERE (generic_file_fk IS NOT NULL);


--
-- Name: audit_record_inserted_when_idx; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX audit_record_inserted_when_idx ON public.audit_record USING btree (inserted_when);


--
-- Name: audit_record_o_procedure_fk_pix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX audit_record_o_procedure_fk_pix ON public.audit_record USING btree (o_procedure_fk) WHERE (o_procedure_fk IS NOT NULL);


--
-- Name: audit_record_orgunit_fk_pix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX audit_record_orgunit_fk_pix ON public.audit_record USING btree (orgunit_fk) WHERE (orgunit_fk IS NOT NULL);


--
-- Name: audit_record_pacs_user_fk_pix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX audit_record_pacs_user_fk_pix ON public.audit_record USING btree (pacs_user_fk) WHERE (pacs_user_fk IS NOT NULL);


--
-- Name: audit_record_patient_fk_pix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX audit_record_patient_fk_pix ON public.audit_record USING btree (patient_fk) WHERE (patient_fk IS NOT NULL);


--
-- Name: audit_record_patient_id_upper_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX audit_record_patient_id_upper_ix ON public.audit_record USING btree (upper((patient_id)::text) text_pattern_ops);


--
-- Name: audit_record_role_fk_pix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX audit_record_role_fk_pix ON public.audit_record USING btree (role_fk) WHERE (role_fk IS NOT NULL);


--
-- Name: audit_record_source_alt_id_upper_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX audit_record_source_alt_id_upper_ix ON public.audit_record USING btree (upper((source_alt_id)::text) text_pattern_ops);


--
-- Name: audit_record_source_id_upper_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX audit_record_source_id_upper_ix ON public.audit_record USING btree (upper((source_id)::text) text_pattern_ops);


--
-- Name: audit_record_source_network_access_point_id_upper_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX audit_record_source_network_access_point_id_upper_ix ON public.audit_record USING btree (upper((source_network_access_point_id)::text) text_pattern_ops);


--
-- Name: audit_record_target_dicom_series_fk_pix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX audit_record_target_dicom_series_fk_pix ON public.audit_record USING btree (target_dicom_series_fk) WHERE (target_dicom_series_fk IS NOT NULL);


--
-- Name: audit_record_target_document_fk_pix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX audit_record_target_document_fk_pix ON public.audit_record USING btree (target_document_fk) WHERE (target_document_fk IS NOT NULL);


--
-- Name: audit_record_target_patient_fk_pix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX audit_record_target_patient_fk_pix ON public.audit_record USING btree (target_patient_fk) WHERE (target_patient_fk IS NOT NULL);


--
-- Name: audit_record_target_visit_fk_pix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX audit_record_target_visit_fk_pix ON public.audit_record USING btree (target_visit_fk) WHERE (target_visit_fk IS NOT NULL);


--
-- Name: audit_record_unarchived_pix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX audit_record_unarchived_pix ON public.audit_record USING btree (event_date_time) WHERE (NOT archived);


--
-- Name: audit_record_user_alt_id_upper_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX audit_record_user_alt_id_upper_ix ON public.audit_record USING btree (upper((user_alt_id)::text) text_pattern_ops);


--
-- Name: audit_record_user_id_upper_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX audit_record_user_id_upper_ix ON public.audit_record USING btree (upper((user_id)::text) text_pattern_ops);


--
-- Name: audit_record_user_name_upper_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX audit_record_user_name_upper_ix ON public.audit_record USING btree (upper((user_name)::text) text_pattern_ops);


--
-- Name: audit_record_visit_fk_pix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX audit_record_visit_fk_pix ON public.audit_record USING btree (visit_fk) WHERE (visit_fk IS NOT NULL);


--
-- Name: cfg_e_b_cfg_e_desc_fk; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX cfg_e_b_cfg_e_desc_fk ON public.config_entry_base USING btree (config_entry_description_fk);


--
-- Name: config_entry_base_product_fk; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX config_entry_base_product_fk ON public.config_entry_base USING btree (product_fk);


--
-- Name: diagnostic_report_effective_date_time_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX diagnostic_report_effective_date_time_ix ON public.diagnostic_report USING btree (effective_date_time);


--
-- Name: diagnostic_report_effective_period_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX diagnostic_report_effective_period_ix ON public.diagnostic_report USING spgist (effective_period);


--
-- Name: diagnostic_report_fhir_conclusioncode_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX diagnostic_report_fhir_conclusioncode_ix ON public.diagnostic_report USING gin (((fhir -> 'conclusionCode'::text)));


--
-- Name: diagnostic_report_fhir_identifier_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX diagnostic_report_fhir_identifier_ix ON public.diagnostic_report USING gin (((fhir -> 'identifier'::text)));


--
-- Name: diagnostic_report_generic_file_fk_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX diagnostic_report_generic_file_fk_ix ON public.diagnostic_report USING btree (generic_file_fk);


--
-- Name: dicimg_architem_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX dicimg_architem_ix ON public.dicom_image USING btree (archive_item_name text_pattern_ops);


--
-- Name: dicimg_delby_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX dicimg_delby_ix ON public.dicom_image USING btree (deleted_by_fk);


--
-- Name: dicimg_dicomser_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX dicimg_dicomser_ix ON public.dicom_image USING btree (dicom_series_fk);


--
-- Name: dicimg_insertby_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX dicimg_insertby_ix ON public.dicom_image USING btree (inserted_by_fk);


--
-- Name: dicimg_inst_statdel0_u; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE UNIQUE INDEX dicimg_inst_statdel0_u ON public.dicom_image USING btree (sop_instance_uid text_pattern_ops) WHERE (status_deleted = 0);


--
-- Name: dicimg_inst_statdel_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX dicimg_inst_statdel_ix ON public.dicom_image USING btree (sop_instance_uid text_pattern_ops, status_deleted);


--
-- Name: dicimg_instancenumber_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX dicimg_instancenumber_ix ON public.dicom_image USING btree (instancenumber);


--
-- Name: dicimg_lastmodby_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX dicimg_lastmodby_ix ON public.dicom_image USING btree (last_modified_by_fk);


--
-- Name: dicimg_statdel_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX dicimg_statdel_ix ON public.dicom_image USING btree (status_deleted);


--
-- Name: dicom_mail_recipient_address; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX dicom_mail_recipient_address ON public.dicom_mail_recipient USING btree (address);


--
-- Name: dicom_mail_recipient_address_upper; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX dicom_mail_recipient_address_upper ON public.dicom_mail_recipient USING btree (upper((address)::text));


--
-- Name: dicse_inst_statdel0_u; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE UNIQUE INDEX dicse_inst_statdel0_u ON public.dicom_series USING btree (series_instance_uid text_pattern_ops) WHERE (status_deleted = 0);


--
-- Name: dicser_delby_i; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX dicser_delby_i ON public.dicom_series USING btree (deleted_by_fk);


--
-- Name: dicser_dicomstudy_i; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX dicser_dicomstudy_i ON public.dicom_series USING btree (dicom_study_fk);


--
-- Name: dicser_insertby_i; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX dicser_insertby_i ON public.dicom_series USING btree (inserted_by_fk);


--
-- Name: dicser_inst_statdel_i; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX dicser_inst_statdel_i ON public.dicom_series USING btree (series_instance_uid text_pattern_ops, status_deleted);


--
-- Name: dicser_lastmodby_i; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX dicser_lastmodby_i ON public.dicom_series USING btree (last_modified_by_fk);


--
-- Name: dicser_modality_i; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX dicser_modality_i ON public.dicom_series USING btree (modality_fk text_pattern_ops);


--
-- Name: dicser_opname_upper_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX dicser_opname_upper_ix ON public.dicom_series USING btree (upper((operatorsname)::text) text_pattern_ops);


--
-- Name: dicser_seriesnumber_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX dicser_seriesnumber_ix ON public.dicom_series USING btree (seriesnumber);


--
-- Name: dicser_step_id_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX dicser_step_id_ix ON public.dicom_series USING btree (step_id text_pattern_ops);


--
-- Name: dicstud_allmod_i; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX dicstud_allmod_i ON public.dicom_study USING btree (all_modalities text_pattern_ops);


--
-- Name: dicstud_calling_aet_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX dicstud_calling_aet_ix ON public.dicom_study USING btree (calling_aet text_pattern_ops);


--
-- Name: dicstud_delby_i; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX dicstud_delby_i ON public.dicom_study USING btree (deleted_by_fk);


--
-- Name: dicstud_id_pk; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE UNIQUE INDEX dicstud_id_pk ON public.dicom_study USING btree (document_fk);


--
-- Name: dicstud_insertby_i; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX dicstud_insertby_i ON public.dicom_study USING btree (inserted_by_fk);


--
-- Name: dicstud_inst_statdel0_u; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE UNIQUE INDEX dicstud_inst_statdel0_u ON public.dicom_study USING btree (study_instance_uid text_pattern_ops) WHERE (status_deleted = 0);


--
-- Name: dicstud_inst_statdel_i; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX dicstud_inst_statdel_i ON public.dicom_study USING btree (study_instance_uid text_pattern_ops, status_deleted);


--
-- Name: dicstud_lastmodby_i; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX dicstud_lastmodby_i ON public.dicom_study USING btree (last_modified_by_fk);


--
-- Name: dicstud_statdel_i; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX dicstud_statdel_i ON public.dicom_study USING btree (status_deleted);


--
-- Name: doc_created_when; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX doc_created_when ON public.document USING btree (document_created_when);


--
-- Name: doc_deleted_by_fk_i; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX doc_deleted_by_fk_i ON public.document USING btree (deleted_by_fk);


--
-- Name: doc_description_i; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX doc_description_i ON public.document USING btree (description text_pattern_ops);


--
-- Name: doc_description_upper_i; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX doc_description_upper_i ON public.document USING btree (upper((description)::text) text_pattern_ops);


--
-- Name: doc_description_upper_tri; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

-- CREATE INDEX doc_description_upper_tri ON public.document USING gin (upper((description)::text) public.gin_trgm_ops);


--
-- Name: doc_document_type_fk_i; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX doc_document_type_fk_i ON public.document USING btree (document_type_fk text_pattern_ops);


--
-- Name: doc_indx01_i; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX doc_indx01_i ON public.document USING btree (index01 text_pattern_ops);


--
-- Name: doc_indx02_i; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX doc_indx02_i ON public.document USING btree (index02 text_pattern_ops);


--
-- Name: doc_indx03_i; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX doc_indx03_i ON public.document USING btree (index03);


--
-- Name: doc_indx04_i; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX doc_indx04_i ON public.document USING btree (index04 text_pattern_ops);


--
-- Name: doc_indx05_i; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX doc_indx05_i ON public.document USING btree (index05 text_pattern_ops);


--
-- Name: doc_indx06_i; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX doc_indx06_i ON public.document USING btree (index06 text_pattern_ops);


--
-- Name: doc_indx07_i; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX doc_indx07_i ON public.document USING btree (index07 text_pattern_ops);


--
-- Name: doc_indx09_i; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX doc_indx09_i ON public.document USING btree (index09 text_pattern_ops);


--
-- Name: doc_indx11_i; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX doc_indx11_i ON public.document USING btree (index11 text_pattern_ops);


--
-- Name: doc_inserted_by_fk_i; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX doc_inserted_by_fk_i ON public.document USING btree (inserted_by_fk);


--
-- Name: doc_instance_uid; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX doc_instance_uid ON public.document USING btree (instance_uid text_pattern_ops);


--
-- Name: doc_instance_uid_statdel0_u; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE UNIQUE INDEX doc_instance_uid_statdel0_u ON public.document USING btree (instance_uid text_pattern_ops) WHERE (status_deleted = 0);


--
-- Name: doc_last_modified_by_fk_i; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX doc_last_modified_by_fk_i ON public.document USING btree (last_modified_by_fk);


--
-- Name: doc_link_patient_visit_fk_i; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX doc_link_patient_visit_fk_i ON public.document USING btree (link_patient_visit_fk);


--
-- Name: doc_orderer_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX doc_orderer_ix ON public.document USING btree (orderer);


--
-- Name: doc_procedure_id_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX doc_procedure_id_ix ON public.document USING btree (procedure_id text_pattern_ops);


--
-- Name: doc_producer_i; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX doc_producer_i ON public.document USING btree (producer);


--
-- Name: doc_rectypfk_i; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX doc_rectypfk_i ON public.document USING btree (record_type_fk);


--
-- Name: doc_share_doc_fk_org_fk_uq; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE UNIQUE INDEX doc_share_doc_fk_org_fk_uq ON public.document_share USING btree (orgunit_fk, document_fk);


--
-- Name: doc_status_deleted_fk_i; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX doc_status_deleted_fk_i ON public.document USING btree (status_deleted);


--
-- Name: document_class_code_value_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX document_class_code_value_ix ON public.document_class USING btree (code_value);


--
-- Name: document_class_coding_scheme_code_value_uq; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE UNIQUE INDEX document_class_coding_scheme_code_value_uq ON public.document_class USING btree (coding_scheme, code_value);


--
-- Name: document_class_coding_scheme_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX document_class_coding_scheme_ix ON public.document_class USING btree (coding_scheme);


--
-- Name: document_class_display_display_name_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX document_class_display_display_name_ix ON public.document_class_display USING btree (display_name);


--
-- Name: document_class_display_kw_lang_uq; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE UNIQUE INDEX document_class_display_kw_lang_uq ON public.document_class_display USING btree (document_class_fk, language_fk);


--
-- Name: document_class_group_name_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX document_class_group_name_ix ON public.document_class_group USING btree (name);


--
-- Name: document_document_fk_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX document_document_fk_ix ON public.document USING btree (document_class_fk);


--
-- Name: document_export_document_fk; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX document_export_document_fk ON public.document_export USING btree (document_fk);


--
-- Name: document_record_subtype_fk; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX document_record_subtype_fk ON public.document USING btree (record_subtype_fk);


--
-- Name: document_share_document_fk; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX document_share_document_fk ON public.document_share USING btree (document_fk);


--
-- Name: document_share_orgunit_fk; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX document_share_orgunit_fk ON public.document_share USING btree (orgunit_fk);


--
-- Name: document_vip_indicator_fk; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX document_vip_indicator_fk ON public.document USING btree (vip_indicator_fk);


--
-- Name: dse_dstf_sd_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX dse_dstf_sd_ix ON public.dicom_series USING btree (dicom_study_fk, status_deleted);


--
-- Name: dst_df_sd_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX dst_df_sd_ix ON public.dicom_study USING btree (document_fk, status_deleted);


--
-- Name: fhir_identifier_system_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX fhir_identifier_system_ix ON public.fhir_identifier USING btree (system);


--
-- Name: fhir_identifier_system_value_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE UNIQUE INDEX fhir_identifier_system_value_ix ON public.fhir_identifier USING btree (system, value);


--
-- Name: fhir_identifier_value_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX fhir_identifier_value_ix ON public.fhir_identifier USING btree (value);


--
-- Name: fsnode_archive_object_is_archived_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX fsnode_archive_object_is_archived_ix ON public.fsnode_archive_object USING btree (is_archived);


--
-- Name: fsnode_archive_object_is_online_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX fsnode_archive_object_is_online_ix ON public.fsnode_archive_object USING btree (is_online);


--
-- Name: fsnode_archive_object_is_verified_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX fsnode_archive_object_is_verified_ix ON public.fsnode_archive_object USING btree (is_verified);


--
-- Name: fsnode_archive_object_last_access_when_online_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX fsnode_archive_object_last_access_when_online_ix ON public.fsnode_archive_object USING btree (last_access_when) WHERE (is_online = true);


--
-- Name: fsnode_archive_object_summary_tbl_node_fk_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE UNIQUE INDEX fsnode_archive_object_summary_tbl_node_fk_ix ON public.fsnode_archive_object_summary USING btree (tbl_node_fk);


--
-- Name: fsnode_injected_file_container_id_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX fsnode_injected_file_container_id_ix ON public.fsnode_injected_file USING btree (container_id);


--
-- Name: fsnode_injected_file_injected_when_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX fsnode_injected_file_injected_when_ix ON public.fsnode_injected_file USING btree (injected_when);


--
-- Name: fsnode_injected_file_tbl_node_fk_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX fsnode_injected_file_tbl_node_fk_ix ON public.fsnode_injected_file USING btree (tbl_node_fk);


--
-- Name: gen_con_deleted_by_fk; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX gen_con_deleted_by_fk ON public.generic_container USING btree (deleted_by_fk);


--
-- Name: gen_con_inserted_by_fk; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX gen_con_inserted_by_fk ON public.generic_container USING btree (inserted_by_fk);


--
-- Name: gen_con_last_modified_by_fk; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX gen_con_last_modified_by_fk ON public.generic_container USING btree (last_modified_by_fk);


--
-- Name: gen_file_last_modified_by_fk; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX gen_file_last_modified_by_fk ON public.generic_file USING btree (last_modified_by_fk);


--
-- Name: gencont_archname_i; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX gencont_archname_i ON public.generic_container USING btree (archive_container_name text_pattern_ops);


--
-- Name: gencont_instanceuid_i; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX gencont_instanceuid_i ON public.generic_container USING btree (generic_container_uid text_pattern_ops);


--
-- Name: generic_container_name_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX generic_container_name_ix ON public.generic_container USING btree (name text_pattern_ops);


--
-- Name: generic_file_deleted_by_fk; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX generic_file_deleted_by_fk ON public.generic_file USING btree (deleted_by_fk);


--
-- Name: generic_file_inserted_by_fk; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX generic_file_inserted_by_fk ON public.generic_file USING btree (inserted_by_fk);


--
-- Name: generic_file_original_name_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX generic_file_original_name_ix ON public.generic_file USING btree (original_name);


--
-- Name: genfile_architem_i; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX genfile_architem_i ON public.generic_file USING btree (archive_item_name text_pattern_ops);


--
-- Name: genfile_extension_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX genfile_extension_ix ON public.generic_file USING btree (extension text_pattern_ops);


--
-- Name: genfile_gencontfk_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX genfile_gencontfk_ix ON public.generic_file USING btree (generic_container_fk);


--
-- Name: genfile_instanceuid_i; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX genfile_instanceuid_i ON public.generic_file USING btree (generic_file_uid text_pattern_ops);


--
-- Name: hl7_notification_explicit_request_id_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX hl7_notification_explicit_request_id_ix ON public.hl7_notification USING btree (explicit_request_id);


--
-- Name: hl7not_documentfk_i; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX hl7not_documentfk_i ON public.hl7_notification USING btree (document_fk);


--
-- Name: hl7not_genfilefk_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX hl7not_genfilefk_ix ON public.hl7_notification USING btree (generic_file_fk);


--
-- Name: hl7not_name_status_fk_i; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX hl7not_name_status_fk_i ON public.hl7_notification USING btree (notification_name text_pattern_ops, hl7_notification_status_fk);


--
-- Name: hl7not_statusfk_i; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX hl7not_statusfk_i ON public.hl7_notification USING btree (hl7_notification_status_fk);


--
-- Name: host_name_uq2; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE UNIQUE INDEX host_name_uq2 ON public.host USING btree (name text_pattern_ops);


--
-- Name: ihe_actor_name; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX ihe_actor_name ON public.ihe_actor USING btree (name);


--
-- Name: ihe_actor_type; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX ihe_actor_type ON public.ihe_actor USING btree (type);


--
-- Name: ihe_actor_uid; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX ihe_actor_uid ON public.ihe_actor USING btree (uid);


--
-- Name: ihe_manifest_dicom_image_fk_ihe_domain_fk_uq; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE UNIQUE INDEX ihe_manifest_dicom_image_fk_ihe_domain_fk_uq ON public.ihe_manifest USING btree (dicom_image_fk, ihe_domain_fk);


--
-- Name: ihe_manifest_generic_file_fk_ihe_domain_fk_uq; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE UNIQUE INDEX ihe_manifest_generic_file_fk_ihe_domain_fk_uq ON public.ihe_manifest USING btree (generic_file_fk, ihe_domain_fk);


--
-- Name: ihe_manifest_unique_id_ihe_domain_fk_uq; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE UNIQUE INDEX ihe_manifest_unique_id_ihe_domain_fk_uq ON public.ihe_manifest USING btree (unique_id, ihe_domain_fk);


--
-- Name: imedone_manifest_generic_file_fk_uq; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE UNIQUE INDEX imedone_manifest_generic_file_fk_uq ON public.imedone_manifest USING btree (generic_file_fk);


--
-- Name: item_version_security_creation_date_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX item_version_security_creation_date_ix ON public.item_version_security USING btree (creation_date);


--
-- Name: item_version_security_prop_item_version_security_fk_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX item_version_security_prop_item_version_security_fk_ix ON public.item_version_security_prop USING btree (item_version_security_fk);


--
-- Name: item_version_security_tbl_item_version_fk_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX item_version_security_tbl_item_version_fk_ix ON public.item_version_security USING btree (tbl_item_version_fk);


--
-- Name: keyword_class_default_fk; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX keyword_class_default_fk ON public.keyword_class USING btree (default_fk);


--
-- Name: keyword_classfk_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX keyword_classfk_ix ON public.keyword USING btree (keyword_class_fk);


--
-- Name: keyword_display_display_name_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX keyword_display_display_name_ix ON public.keyword_display USING btree (display_name);


--
-- Name: keyword_display_kw_lang_uq; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE UNIQUE INDEX keyword_display_kw_lang_uq ON public.keyword_display USING btree (keyword_fk, language_fk);


--
-- Name: keyword_numeric_value_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX keyword_numeric_value_ix ON public.keyword USING btree (num_value);


--
-- Name: keyword_obs_float_value_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX keyword_obs_float_value_ix ON public.keyword USING btree (obs_float_value);


--
-- Name: keyword_parent_fk; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX keyword_parent_fk ON public.keyword USING btree (parent_fk);


--
-- Name: keyword_pure_date_value_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX keyword_pure_date_value_ix ON public.keyword USING btree (date_value);


--
-- Name: keyword_timestamp_value_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX keyword_timestamp_value_ix ON public.keyword USING btree (timestamp_value);


--
-- Name: keyword_value_md5; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX keyword_value_md5 ON public.keyword USING btree (md5((value)::text));


--
-- Name: keyword_value_upper_tri; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

-- CREATE INDEX keyword_value_upper_tri ON public.keyword USING gin (upper((value)::text) public.gin_trgm_ops);


--
-- Name: kw_cl_kw_cl_grp_fk; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX kw_cl_kw_cl_grp_fk ON public.keyword_class USING btree (keyword_class_group_fk text_pattern_ops);


--
-- Name: kw_class_name_upper_uq; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE UNIQUE INDEX kw_class_name_upper_uq ON public.keyword_class USING btree (upper((name)::text));


--
-- Name: kw_class_name_upper_uq2; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE UNIQUE INDEX kw_class_name_upper_uq2 ON public.keyword_class USING btree (upper((name)::text) text_pattern_ops);


--
-- Name: kw_class_name_uq2; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE UNIQUE INDEX kw_class_name_uq2 ON public.keyword_class USING btree (name text_pattern_ops);


--
-- Name: lah_aet_fk; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX lah_aet_fk ON public.link_aet_host USING btree (aet_fk);


--
-- Name: lah_host_fk; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX lah_host_fk ON public.link_aet_host USING btree (host_fk);


--
-- Name: ldckw_document_class_fk_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX ldckw_document_class_fk_ix ON public.link_document_class_keyword USING btree (document_class_fk);


--
-- Name: ldckw_keyword_fk_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX ldckw_keyword_fk_ix ON public.link_document_class_keyword USING btree (keyword_fk);


--
-- Name: ldikw_di_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX ldikw_di_ix ON public.link_dicom_image_keyword USING btree (dicom_image_fk);


--
-- Name: ldikw_difk_kwfk_pk; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE UNIQUE INDEX ldikw_difk_kwfk_pk ON public.link_dicom_image_keyword USING btree (dicom_image_fk, keyword_fk);


--
-- Name: ldikw_kw_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX ldikw_kw_ix ON public.link_dicom_image_keyword USING btree (keyword_fk);


--
-- Name: ldkw_d_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX ldkw_d_ix ON public.link_document_keyword USING btree (document_fk);


--
-- Name: ldkw_kw_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX ldkw_kw_ix ON public.link_document_keyword USING btree (keyword_fk);


--
-- Name: ldkwa_d_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX ldkwa_d_ix ON public.link_document_keyword_all USING btree (document_fk);


--
-- Name: ldkwa_kw_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX ldkwa_kw_ix ON public.link_document_keyword_all USING btree (keyword_fk);


--
-- Name: ldskw_ds_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX ldskw_ds_ix ON public.link_dicom_series_keyword USING btree (dicom_series_fk);


--
-- Name: ldskw_kw_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX ldskw_kw_ix ON public.link_dicom_series_keyword USING btree (keyword_fk);


--
-- Name: lgfkw_gf_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX lgfkw_gf_ix ON public.link_generic_file_keyword USING btree (generic_file_fk);


--
-- Name: lgfkw_kw_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX lgfkw_kw_ix ON public.link_generic_file_keyword USING btree (keyword_fk);


--
-- Name: link_diagnostic_report_fhir_identifier_diagnostic_report_fk_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX link_diagnostic_report_fhir_identifier_diagnostic_report_fk_ix ON public.link_diagnostic_report_fhir_identifier USING btree (diagnostic_report_fk);


--
-- Name: link_diagnostic_report_fhir_identifier_fhir_identifier_fk_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE UNIQUE INDEX link_diagnostic_report_fhir_identifier_fhir_identifier_fk_ix ON public.link_diagnostic_report_fhir_identifier USING btree (fhir_identifier_fk);


--
-- Name: link_dicom_image_image_marker_dicom_image_fk_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX link_dicom_image_image_marker_dicom_image_fk_ix ON public.link_dicom_image_image_marker USING btree (dicom_image_fk);


--
-- Name: link_dicom_image_image_marker_image_marker_fk_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX link_dicom_image_image_marker_image_marker_fk_ix ON public.link_dicom_image_image_marker USING btree (image_marker_fk);


--
-- Name: link_document_class_group_group_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX link_document_class_group_group_ix ON public.link_document_class_group USING btree (document_class_group_fk);


--
-- Name: link_document_document_marker_document_fk_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX link_document_document_marker_document_fk_ix ON public.link_document_document_marker USING btree (document_fk);


--
-- Name: link_document_document_marker_document_marker_fk_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX link_document_document_marker_document_marker_fk_ix ON public.link_document_document_marker USING btree (document_marker_fk);


--
-- Name: link_ext_ext_grp_ext_grp_uq; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE UNIQUE INDEX link_ext_ext_grp_ext_grp_uq ON public.link_extension_extension_group USING btree (extension_fk, extension_group_fk);


--
-- Name: link_generic_file_image_marker_generic_file_fk_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX link_generic_file_image_marker_generic_file_fk_ix ON public.link_generic_file_image_marker USING btree (generic_file_fk);


--
-- Name: link_generic_file_image_marker_image_marker_fk_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX link_generic_file_image_marker_image_marker_fk_ix ON public.link_generic_file_image_marker USING btree (image_marker_fk);


--
-- Name: link_kw_cl_grp_l_kw_cl_grp_fk; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX link_kw_cl_grp_l_kw_cl_grp_fk ON public.link_keyword_class_group_level USING btree (keyword_class_group_fk text_pattern_ops);


--
-- Name: link_kw_cl_grp_l_kw_l_fk; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX link_kw_cl_grp_l_kw_l_fk ON public.link_keyword_class_group_level USING btree (keyword_level_fk text_pattern_ops);


--
-- Name: link_medication_administration_fhir_identifier_ident_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE UNIQUE INDEX link_medication_administration_fhir_identifier_ident_ix ON public.link_medication_administration_fhir_identifier USING btree (fhir_identifier_fk);


--
-- Name: link_medication_administration_fhir_identifier_medadmin_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX link_medication_administration_fhir_identifier_medadmin_ix ON public.link_medication_administration_fhir_identifier USING btree (medication_administration_fk);


--
-- Name: link_mpps_info_o_procedure_mpps_info_fk_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX link_mpps_info_o_procedure_mpps_info_fk_ix ON public.link_mpps_info_o_procedure USING btree (mpps_info_fk);


--
-- Name: link_observation_fhir_identifier_fhir_identifier_fk_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE UNIQUE INDEX link_observation_fhir_identifier_fhir_identifier_fk_ix ON public.link_observation_fhir_identifier USING btree (fhir_identifier_fk);


--
-- Name: link_observation_fhir_identifier_observation_fk_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX link_observation_fhir_identifier_observation_fk_ix ON public.link_observation_fhir_identifier USING btree (observation_fk);


--
-- Name: link_presentation_document_document_fk_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX link_presentation_document_document_fk_ix ON public.link_presentation_document USING btree (document_fk);


--
-- Name: link_presentation_document_presentation_fk_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX link_presentation_document_presentation_fk_ix ON public.link_presentation_document USING btree (presentation_fk);


--
-- Name: link_report_item_version_security_item_version_security_fk_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX link_report_item_version_security_item_version_security_fk_ix ON public.link_report_item_version_security USING btree (item_version_security_fk);


--
-- Name: link_report_item_version_security_report_fk_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX link_report_item_version_security_report_fk_ix ON public.link_report_item_version_security USING btree (report_fk);


--
-- Name: link_role_perm_perm_fk; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX link_role_perm_perm_fk ON public.link_role_permission USING btree (permission_fk text_pattern_ops);


--
-- Name: link_role_permission_role_fk; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX link_role_permission_role_fk ON public.link_role_permission USING btree (role_fk text_pattern_ops);


--
-- Name: link_snapshot_document_document_fk_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX link_snapshot_document_document_fk_ix ON public.link_snapshot_document USING btree (document_fk);


--
-- Name: link_snapshot_document_snapshot_fk_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX link_snapshot_document_snapshot_fk_ix ON public.link_snapshot_document USING btree (snapshot_fk);


--
-- Name: link_tn_tiv_fao_fsnode_archive_object_fk_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX link_tn_tiv_fao_fsnode_archive_object_fk_ix ON public.link_tbl_node_tbl_item_version_fsnode_archive_object USING btree (fsnode_archive_object_fk);


--
-- Name: link_tn_tiv_fao_tbl_instance_fk_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX link_tn_tiv_fao_tbl_instance_fk_ix ON public.link_tbl_node_tbl_item_version_fsnode_archive_object USING btree (tbl_node_fk);


--
-- Name: link_tn_tiv_fao_tbl_item_version_fk_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX link_tn_tiv_fao_tbl_item_version_fk_ix ON public.link_tbl_node_tbl_item_version_fsnode_archive_object USING btree (tbl_item_version_fk);


--
-- Name: lopkw_keyword_fk; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX lopkw_keyword_fk ON public.link_o_procedure_keyword USING btree (keyword_fk);


--
-- Name: lopkw_procedure_fk; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX lopkw_procedure_fk ON public.link_o_procedure_keyword USING btree (o_procedure_fk);


--
-- Name: lpv_nulllpv_uq; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE UNIQUE INDEX lpv_nulllpv_uq ON public.link_patient_visit USING btree (patient_fk) WHERE (visit_fk IS NULL);


--
-- Name: lpv_patfk_i; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX lpv_patfk_i ON public.link_patient_visit USING btree (patient_fk);


--
-- Name: lrpu_pu_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX lrpu_pu_ix ON public.link_role_pacs_user USING btree (pacs_user_fk);


--
-- Name: lrpu_r_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX lrpu_r_ix ON public.link_role_pacs_user USING btree (role_fk text_pattern_ops);


--
-- Name: medication_administration_effective_date_time_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX medication_administration_effective_date_time_ix ON public.medication_administration USING btree (effective_date_time);


--
-- Name: medication_administration_effective_period_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX medication_administration_effective_period_ix ON public.medication_administration USING spgist (effective_period);


--
-- Name: medication_administration_generic_file_fk_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX medication_administration_generic_file_fk_ix ON public.medication_administration USING btree (generic_file_fk);


--
-- Name: medication_administration_medication_code_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX medication_administration_medication_code_ix ON public.medication_administration USING gin (((fhir -> 'medicationCodeableConcept'::text)));


--
-- Name: medication_administration_not_given_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX medication_administration_not_given_ix ON public.medication_administration USING btree ((((fhir ->> 'notGiven'::text))::boolean));


--
-- Name: medication_administration_status_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX medication_administration_status_ix ON public.medication_administration USING btree (((fhir ->> 'status'::text)));


--
-- Name: modifier_description_uq; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE UNIQUE INDEX modifier_description_uq ON public.modifier USING btree (description);


--
-- Name: mpps_image_info_uq; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE UNIQUE INDEX mpps_image_info_uq ON public.mpps_image_info USING btree (sop_instance_uid, mpps_series_info_fk);


--
-- Name: mpps_info_dose_report_created_pix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX mpps_info_dose_report_created_pix ON public.mpps_info USING btree (dose_report_created) WHERE (dose_report_created = false);


--
-- Name: mpps_info_ian_sent_pix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX mpps_info_ian_sent_pix ON public.mpps_info USING btree (ian_sent) WHERE (ian_sent = false);


--
-- Name: mpps_info_mpps_sop_instance_uid_uq; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE UNIQUE INDEX mpps_info_mpps_sop_instance_uid_uq ON public.mpps_info USING btree (mpps_sop_instance_uid);


--
-- Name: mpps_series_info_uq; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE UNIQUE INDEX mpps_series_info_uq ON public.mpps_series_info USING btree (series_instance_uid, mpps_info_fk);


--
-- Name: notif_docfk_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX notif_docfk_ix ON public.notification USING btree (document_fk);


--
-- Name: notif_eventfk_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX notif_eventfk_ix ON public.notification USING btree (notification_event_fk);


--
-- Name: notif_name_event_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX notif_name_event_ix ON public.notification USING btree (name, notification_event_fk);


--
-- Name: notif_name_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX notif_name_ix ON public.notification USING btree (name);


--
-- Name: notif_name_processed_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX notif_name_processed_ix ON public.notification USING btree (name text_pattern_ops, processed);


--
-- Name: notif_proc_ev_doc_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX notif_proc_ev_doc_ix ON public.notification USING btree (processed, notification_event_fk, document_fk);


--
-- Name: notif_proc_name_ev_doc_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX notif_proc_name_ev_doc_ix ON public.notification USING btree (processed, name text_pattern_ops, notification_event_fk, document_fk);


--
-- Name: notification_inserted_when_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX notification_inserted_when_ix ON public.notification USING btree (inserted_when);


--
-- Name: notification_processed_when_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX notification_processed_when_ix ON public.notification USING btree (processed_when);


--
-- Name: notification_processed_when_nf_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX notification_processed_when_nf_ix ON public.notification USING btree (processed_when NULLS FIRST);


--
-- Name: notification_type_name_ev_uq; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE UNIQUE INDEX notification_type_name_ev_uq ON public.notification_type USING btree (name, notification_event_fk);


--
-- Name: notiftyp_notifeve_i; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX notiftyp_notifeve_i ON public.notification_type USING btree (notification_event_fk);


--
-- Name: o_procedure_accession_number_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX o_procedure_accession_number_ix ON public.o_procedure USING btree (accession_number text_pattern_ops);


--
-- Name: o_procedure_document_class_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX o_procedure_document_class_ix ON public.o_procedure USING btree (document_class_fk);


--
-- Name: o_procedure_external_id_uq; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE UNIQUE INDEX o_procedure_external_id_uq ON public.o_procedure USING btree (external_id text_pattern_ops);


--
-- Name: o_procedure_filled_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX o_procedure_filled_ix ON public.o_procedure USING btree (filled);


--
-- Name: o_procedure_filler_order_number; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX o_procedure_filler_order_number ON public.o_procedure USING btree (filler_order_number text_pattern_ops);


--
-- Name: o_procedure_inserted_when_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX o_procedure_inserted_when_ix ON public.o_procedure USING btree (inserted_when);


--
-- Name: o_procedure_last_modified_when_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX o_procedure_last_modified_when_ix ON public.o_procedure USING btree (last_modified_when);


--
-- Name: o_procedure_link_patient_visit_fk; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX o_procedure_link_patient_visit_fk ON public.o_procedure USING btree (link_patient_visit_fk);


--
-- Name: o_procedure_modality_fk; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX o_procedure_modality_fk ON public.o_procedure USING btree (modality_fk text_pattern_ops);


--
-- Name: o_procedure_order_id_uq; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE UNIQUE INDEX o_procedure_order_id_uq ON public.o_procedure USING btree (accession_number text_pattern_ops, procedure_id text_pattern_ops, step_id text_pattern_ops);


--
-- Name: o_procedure_orderer_fk; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX o_procedure_orderer_fk ON public.o_procedure USING btree (orderer_fk);


--
-- Name: o_procedure_placer_order_number; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX o_procedure_placer_order_number ON public.o_procedure USING btree (placer_order_number text_pattern_ops);


--
-- Name: o_procedure_procedure_id; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX o_procedure_procedure_id ON public.o_procedure USING btree (procedure_id text_pattern_ops);


--
-- Name: o_procedure_scheduled_station_fk; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX o_procedure_scheduled_station_fk ON public.o_procedure USING btree (scheduled_station_fk);


--
-- Name: o_procedure_step_id; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX o_procedure_step_id ON public.o_procedure USING btree (step_id text_pattern_ops);


--
-- Name: o_procedure_step_start_date; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX o_procedure_step_start_date ON public.o_procedure USING btree (step_start_date);


--
-- Name: o_procedure_study_instance_uid; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX o_procedure_study_instance_uid ON public.o_procedure USING btree (study_instance_uid text_pattern_ops);


--
-- Name: observation_diagnostic_report_fk_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX observation_diagnostic_report_fk_ix ON public.observation USING btree (diagnostic_report_fk);


--
-- Name: observation_effective_date_time_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX observation_effective_date_time_ix ON public.observation USING btree (effective_date_time);


--
-- Name: observation_effective_period_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX observation_effective_period_ix ON public.observation USING spgist (effective_period);


--
-- Name: observation_fhir_code_coding_gin_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX observation_fhir_code_coding_gin_ix ON public.observation USING gin ((((fhir -> 'code'::text) -> 'coding'::text)));


--
-- Name: observation_fhir_code_value_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX observation_fhir_code_value_ix ON public.observation USING btree ((((fhir -> 'code'::text) ->> 'text'::text)));


--
-- Name: observation_fhir_code_value_upper_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX observation_fhir_code_value_upper_ix ON public.observation USING btree (upper(((fhir -> 'code'::text) ->> 'text'::text)));


--
-- Name: observation_fhir_code_value_upper_tri_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

-- CREATE INDEX observation_fhir_code_value_upper_tri_ix ON public.observation USING gin (upper(((fhir -> 'code'::text) ->> 'text'::text)) public.gin_trgm_ops);


--
-- Name: observation_fhir_identifier_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX observation_fhir_identifier_ix ON public.observation USING gin (((fhir -> 'identifier'::text)));


--
-- Name: observation_fhir_valuecode_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX observation_fhir_valuecode_ix ON public.observation USING gin (((fhir -> 'valueCodeableConcept'::text)));


--
-- Name: observation_fhir_valuequantity_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX observation_fhir_valuequantity_ix ON public.observation USING spgist (public.synedra_observation_get_quantity_value_range((((fhir -> 'valueQuantity'::text) ->> 'value'::text))::numeric, ((fhir -> 'valueQuantity'::text) ->> 'comparator'::text)));


--
-- Name: observation_fhir_valuequantity_unit_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX observation_fhir_valuequantity_unit_ix ON public.observation USING btree ((((fhir -> 'valueQuantity'::text) ->> 'unit'::text)));


--
-- Name: observation_fhir_valuerange_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX observation_fhir_valuerange_ix ON public.observation USING spgist (numrange(((((fhir -> 'valueRange'::text) -> 'low'::text) ->> 'value'::text))::numeric, ((((fhir -> 'valueRange'::text) -> 'high'::text) ->> 'value'::text))::numeric, '[]'::text));


--
-- Name: observation_fhir_valuerange_unit_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX observation_fhir_valuerange_unit_ix ON public.observation USING btree (COALESCE((((fhir -> 'valueRange'::text) -> 'low'::text) ->> 'unit'::text), (((fhir -> 'valueRange'::text) -> 'high'::text) ->> 'unit'::text)));


--
-- Name: observation_fhir_valuestring_md5; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX observation_fhir_valuestring_md5 ON public.observation USING btree (md5((fhir ->> 'valueString'::text)));


--
-- Name: observation_fhir_valuestring_upper_tri_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

-- CREATE INDEX observation_fhir_valuestring_upper_tri_ix ON public.observation USING gin (upper((fhir ->> 'valueString'::text)) public.gin_trgm_ops);


--
-- Name: observation_generic_file_fk_effective_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX observation_generic_file_fk_effective_ix ON public.observation USING btree (generic_file_fk, COALESCE(effective_date_time, upper(effective_period), lower(effective_period))) INCLUDE (effective_date_time, effective_period);


--
-- Name: observation_generic_file_fk_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX observation_generic_file_fk_ix ON public.observation USING btree (generic_file_fk);


--
-- Name: observation_specimen_accession_identifier_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX observation_specimen_accession_identifier_ix ON public.observation USING btree (specimen_accession_identifier);


--
-- Name: observation_specimen_collected_date_time_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX observation_specimen_collected_date_time_ix ON public.observation USING btree (specimen_collected_date_time);


--
-- Name: observation_specimen_collected_period_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX observation_specimen_collected_period_ix ON public.observation USING spgist (specimen_collected_period);


--
-- Name: observation_specimen_status_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX observation_specimen_status_ix ON public.observation USING btree (specimen_status);


--
-- Name: observation_value_date_time_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX observation_value_date_time_ix ON public.observation USING btree (value_date_time);


--
-- Name: order_add_info_entry_fk_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX order_add_info_entry_fk_ix ON public.order_additional_info USING btree (order_entry_fk);


--
-- Name: order_e_accnum_uq2; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE UNIQUE INDEX order_e_accnum_uq2 ON public.order_entry USING btree (accession_number text_pattern_ops);


--
-- Name: order_e_effective_when_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX order_e_effective_when_ix ON public.order_entry USING btree (effective_when_begin, effective_when_end);


--
-- Name: order_e_extid_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX order_e_extid_ix ON public.order_entry USING btree (external_id text_pattern_ops);


--
-- Name: order_e_hist_modifier_fk; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX order_e_hist_modifier_fk ON public.order_entry_history USING btree (modifier_fk);


--
-- Name: order_e_hist_order_e_fk; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX order_e_hist_order_e_fk ON public.order_entry_history USING btree (order_entry_fk);


--
-- Name: order_e_hist_order_s_fk; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX order_e_hist_order_s_fk ON public.order_entry_history USING btree (order_status_fk text_pattern_ops);


--
-- Name: order_e_order_e_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX order_e_order_e_ix ON public.order_entry USING btree (order_entry_fk);


--
-- Name: order_e_series_uq2; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE UNIQUE INDEX order_e_series_uq2 ON public.order_entry USING btree (series_instance_uid text_pattern_ops);


--
-- Name: order_e_service_location_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX order_e_service_location_ix ON public.order_entry USING btree (service_location_fk text_pattern_ops);


--
-- Name: order_e_study_uq2; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE UNIQUE INDEX order_e_study_uq2 ON public.order_entry USING btree (study_instance_uid text_pattern_ops);


--
-- Name: order_entry_modality_fk; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX order_entry_modality_fk ON public.order_entry USING btree (modality_fk text_pattern_ops);


--
-- Name: order_entry_order_status_fk; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX order_entry_order_status_fk ON public.order_entry USING btree (order_status_fk text_pattern_ops);


--
-- Name: orderer_oentry_fk_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX orderer_oentry_fk_ix ON public.orderer USING btree (order_entry_fk);


--
-- Name: orderer_ou_fk_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX orderer_ou_fk_ix ON public.orderer USING btree (orgunit_fk text_pattern_ops);


--
-- Name: orgu_abk_uq2; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE UNIQUE INDEX orgu_abk_uq2 ON public.orgunit USING btree (abk text_pattern_ops);


--
-- Name: orgu_isorderer_i; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX orgu_isorderer_i ON public.orgunit USING btree (is_orderer);


--
-- Name: orgu_isproducer_i; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX orgu_isproducer_i ON public.orgunit USING btree (is_producer);


--
-- Name: orgunit_inserted_by_fk; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX orgunit_inserted_by_fk ON public.orgunit USING btree (inserted_by_fk);


--
-- Name: orgunit_last_modified_by_fk; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX orgunit_last_modified_by_fk ON public.orgunit USING btree (last_modified_by_fk);


--
-- Name: orgunit_orgunit_fk; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX orgunit_orgunit_fk ON public.orgunit USING btree (orgunit_fk);


--
-- Name: pacs_session_host_fk; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX pacs_session_host_fk ON public.pacs_session USING btree (host_fk);


--
-- Name: pacs_session_pacs_user_fk; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX pacs_session_pacs_user_fk ON public.pacs_session USING btree (pacs_user_fk);


--
-- Name: pacs_session_prm_prm_fk; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX pacs_session_prm_prm_fk ON public.pacs_session_permission USING btree (permission_fk text_pattern_ops);


--
-- Name: pacs_user_attribute_user_key_uq; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE UNIQUE INDEX pacs_user_attribute_user_key_uq ON public.pacs_user_attribute USING btree (pacs_user_fk, key);


--
-- Name: pacs_user_inserted_by_fk; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX pacs_user_inserted_by_fk ON public.pacs_user USING btree (inserted_by_fk);


--
-- Name: pacs_user_last_active_when_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX pacs_user_last_active_when_ix ON public.pacs_user USING btree (last_active_when);


--
-- Name: pacs_user_last_modified_by_fk; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX pacs_user_last_modified_by_fk ON public.pacs_user USING btree (last_modified_by_fk);


--
-- Name: pacs_user_token_purpose_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX pacs_user_token_purpose_ix ON public.pacs_user_token USING btree (purpose);


--
-- Name: pacs_user_token_user_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX pacs_user_token_user_ix ON public.pacs_user_token USING btree (pacs_user_fk);


--
-- Name: pacssessparam_pacssessperm_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX pacssessparam_pacssessperm_ix ON public.pacs_session_parameter USING btree (pacs_session_permission_fk);


--
-- Name: pacssessperm_pacssess_fk_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX pacssessperm_pacssess_fk_ix ON public.pacs_session_permission USING btree (pacs_session_fk);


--
-- Name: pat_7cols_i; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX pat_7cols_i ON public.patient USING btree (patient_id text_pattern_ops, last_name text_pattern_ops, first_name text_pattern_ops, sex_fk, birth_date, status_deleted, patient_invalidated_by_fk);


--
-- Name: pat_deleted_by_fk_i; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX pat_deleted_by_fk_i ON public.patient USING btree (deleted_by_fk);


--
-- Name: pat_inserted_by_fk_i; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX pat_inserted_by_fk_i ON public.patient USING btree (inserted_by_fk);


--
-- Name: pat_last_modified_by_fk_i; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX pat_last_modified_by_fk_i ON public.patient USING btree (last_modified_by_fk);


--
-- Name: pat_last_name_i; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX pat_last_name_i ON public.patient USING btree (last_name text_pattern_ops);


--
-- Name: pat_lastfirst_i; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX pat_lastfirst_i ON public.patient USING btree (last_name text_pattern_ops, first_name text_pattern_ops);


--
-- Name: pat_pat_invalid_by_fk_i; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX pat_pat_invalid_by_fk_i ON public.patient USING btree (patient_invalidated_by_fk);


--
-- Name: pat_status_deleted_i; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX pat_status_deleted_i ON public.patient USING btree (status_deleted);


--
-- Name: pat_vip_ind; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX pat_vip_ind ON public.patient USING btree (vip_indicator_fk);


--
-- Name: patient_statdel0_u1; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE UNIQUE INDEX patient_statdel0_u1 ON public.patient USING btree (patient_id, last_name, first_name, sex_fk, birth_date) WHERE ((status_deleted = 0) AND (birth_date IS NOT NULL));


--
-- Name: patient_statdel0_u2; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE UNIQUE INDEX patient_statdel0_u2 ON public.patient USING btree (patient_id, last_name, first_name, sex_fk) WHERE ((status_deleted = 0) AND (birth_date IS NULL));


--
-- Name: perm_param_desc_permfk_uq; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX perm_param_desc_permfk_uq ON public.permission_parameter_desc USING btree (permission_fk);


--
-- Name: presentation_created_by_fk_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX presentation_created_by_fk_ix ON public.presentation USING btree (created_by_fk);


--
-- Name: presentation_created_when; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX presentation_created_when ON public.presentation USING btree (created_when);


--
-- Name: presentation_name_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX presentation_name_ix ON public.presentation USING btree (name);


--
-- Name: puser_employeeid_uq2; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE UNIQUE INDEX puser_employeeid_uq2 ON public.pacs_user USING btree (employeeid text_pattern_ops);


--
-- Name: puser_login_uq2; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE UNIQUE INDEX puser_login_uq2 ON public.pacs_user USING btree (login text_pattern_ops);


--
-- Name: role_pkey2; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE UNIQUE INDEX role_pkey2 ON public.role USING btree (name text_pattern_ops);


--
-- Name: scheduler_application_i; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX scheduler_application_i ON public.scheduler_request USING btree (application text_pattern_ops);


--
-- Name: scheduler_host_i; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX scheduler_host_i ON public.scheduler_request USING btree (host text_pattern_ops);


--
-- Name: send_process_message_dicom_image_fk; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX send_process_message_dicom_image_fk ON public.send_process_message USING btree (dicom_image_fk);


--
-- Name: send_process_message_generic_file_fk; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX send_process_message_generic_file_fk ON public.send_process_message USING btree (generic_file_fk);


--
-- Name: send_process_message_send_process_fk; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX send_process_message_send_process_fk ON public.send_process_message USING btree (send_process_fk);


--
-- Name: send_process_pacs_user_fk; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX send_process_pacs_user_fk ON public.send_process USING btree (pacs_user_fk);


--
-- Name: snapshot_created_by_fk_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX snapshot_created_by_fk_ix ON public.snapshot USING btree (created_by_fk);


--
-- Name: snapshot_created_when; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX snapshot_created_when ON public.snapshot USING btree (created_when);


--
-- Name: snapshot_description_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX snapshot_description_ix ON public.snapshot USING btree (description);


--
-- Name: snapshot_type_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX snapshot_type_ix ON public.snapshot USING btree (type);


--
-- Name: storage_commitment_dicom_image_fk_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX storage_commitment_dicom_image_fk_ix ON public.storage_commitment USING btree (dicom_image_fk);


--
-- Name: storage_rule_name_uq; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE UNIQUE INDEX storage_rule_name_uq ON public.storage_rule USING btree (name);


--
-- Name: storage_rule_type_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX storage_rule_type_ix ON public.storage_rule USING btree (type);


--
-- Name: storage_rule_weight_uq; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE UNIQUE INDEX storage_rule_weight_uq ON public.storage_rule USING btree (weight);


--
-- Name: tbl_item_name_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX tbl_item_name_ix ON public.tbl_item USING btree (name text_pattern_ops);


--
-- Name: tbl_item_version_identifier_identifier_uq; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE UNIQUE INDEX tbl_item_version_identifier_identifier_uq ON public.tbl_item_version_identifier USING btree (identifier);


--
-- Name: tbl_item_version_identifier_tbl_item_version_fk_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX tbl_item_version_identifier_tbl_item_version_fk_ix ON public.tbl_item_version_identifier USING btree (tbl_item_version_fk);


--
-- Name: tblcont_creationdate_name_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX tblcont_creationdate_name_ix ON public.tbl_container USING btree (creation_date, name text_pattern_ops);


--
-- Name: tblcont_id_name_uq; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE UNIQUE INDEX tblcont_id_name_uq ON public.tbl_container USING btree (id, name);


--
-- Name: tblinst_container_fk_master1_u; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE UNIQUE INDEX tblinst_container_fk_master1_u ON public.tbl_instance USING btree (tbl_container_fk) WHERE (ismaster = true);


--
-- Name: tblinst_contfk_i; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX tblinst_contfk_i ON public.tbl_instance USING btree (tbl_container_fk);


--
-- Name: tblinst_creationdate_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX tblinst_creationdate_ix ON public.tbl_instance USING btree (creation_date);


--
-- Name: tblinst_node_date_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX tblinst_node_date_ix ON public.tbl_instance USING btree (tbl_node_fk, creation_date);


--
-- Name: tblinst_node_master_date_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX tblinst_node_master_date_ix ON public.tbl_instance USING btree (tbl_node_fk, ismaster, creation_date);


--
-- Name: tblinst_nodefk_i; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX tblinst_nodefk_i ON public.tbl_instance USING btree (tbl_node_fk);


--
-- Name: tblitem_contfk_i; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX tblitem_contfk_i ON public.tbl_item USING btree (tbl_container_fk);


--
-- Name: tblitemver_creation_date_bri; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX tblitemver_creation_date_bri ON public.tbl_item_version USING brin (creation_date);


--
-- Name: tblitemver_itemfk_i; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX tblitemver_itemfk_i ON public.tbl_item_version USING btree (tbl_item_fk);


--
-- Name: tbllock_contfk_i; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX tbllock_contfk_i ON public.tbl_lock USING btree (tbl_container_fk);


--
-- Name: treatment_active_orgunit_pix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX treatment_active_orgunit_pix ON public.treatment USING btree (orgunit_fk) WHERE active;


--
-- Name: treatment_detail_source_uq; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE UNIQUE INDEX treatment_detail_source_uq ON public.treatment_detail USING btree (treatment_fk, source_type, source_id, source_subid);


--
-- Name: treatment_detail_start_end_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX treatment_detail_start_end_ix ON public.treatment_detail USING btree (start_time, end_time);


--
-- Name: treatment_detail_start_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX treatment_detail_start_ix ON public.treatment_detail USING btree (start_time);


--
-- Name: treatment_orgunit_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX treatment_orgunit_ix ON public.treatment USING btree (orgunit_fk);


--
-- Name: treatment_patient_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX treatment_patient_ix ON public.treatment USING btree (patient_fk);


--
-- Name: treatment_patient_orgunit_uq; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE UNIQUE INDEX treatment_patient_orgunit_uq ON public.treatment USING btree (patient_fk, orgunit_fk);


--
-- Name: uid_chain_type_old_i; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE UNIQUE INDEX uid_chain_type_old_i ON public.uid_chain USING btree (type, old_uid);


--
-- Name: view_by_pid_pacs_session_ix; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX view_by_pid_pacs_session_ix ON public.view_by_pid USING btree (pacs_session_fk);


--
-- Name: visit_deleter_i; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX visit_deleter_i ON public.visit USING btree (deleted_by_fk);


--
-- Name: visit_discdisp_i; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX visit_discdisp_i ON public.visit USING btree (discharge_disposition_fk);


--
-- Name: visit_inserter_i; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX visit_inserter_i ON public.visit USING btree (inserted_by_fk);


--
-- Name: visit_modifier_i; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX visit_modifier_i ON public.visit USING btree (last_modified_by_fk);


--
-- Name: visit_patclass_i; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX visit_patclass_i ON public.visit USING btree (patient_class_fk text_pattern_ops);


--
-- Name: visit_preadmitnr_i; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX visit_preadmitnr_i ON public.visit USING btree (preadmit_number text_pattern_ops);


--
-- Name: visit_statdel0_u; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE UNIQUE INDEX visit_statdel0_u ON public.visit USING btree (preadmit_number) WHERE (status_deleted = 0);


--
-- Name: visit_statdel_i; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX visit_statdel_i ON public.visit USING btree (status_deleted);


--
-- Name: visit_vipind_i; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX visit_vipind_i ON public.visit USING btree (vip_indicator_fk);


--
-- Name: visit_visit_invalidated_by_fk; Type: INDEX; Schema: public; Owner: -; Tablespace: syn_idx
--

CREATE INDEX visit_visit_invalidated_by_fk ON public.visit USING btree (visit_invalidated_by_fk);


--
-- Name: ar_file tai_ar_file; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER tai_ar_file AFTER INSERT ON public.ar_file FOR EACH ROW EXECUTE PROCEDURE public.trigger_fct_tai_ar_file();


--
-- Name: link_tbl_node_tbl_item_version_fsnode_archive_object tai_link_tn_tiv_fao; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER tai_link_tn_tiv_fao AFTER INSERT ON public.link_tbl_node_tbl_item_version_fsnode_archive_object FOR EACH ROW EXECUTE PROCEDURE public.trigger_fct_tai_link_tn_tiv_fao();


--
-- Name: link_dicom_image_keyword taiu_ldikw_update_ldkwa; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER taiu_ldikw_update_ldkwa AFTER INSERT OR UPDATE ON public.link_dicom_image_keyword FOR EACH ROW EXECUTE PROCEDURE public.trigger_fct_taiu_ldikw_update_ldkwa();


--
-- Name: link_document_keyword taiu_ldkw_update_ldkwa; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER taiu_ldkw_update_ldkwa AFTER INSERT OR UPDATE ON public.link_document_keyword FOR EACH ROW EXECUTE PROCEDURE public.trigger_fct_taiu_ldkw_update_ldkwa();


--
-- Name: link_dicom_series_keyword taiu_ldskw_update_ldkwa; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER taiu_ldskw_update_ldkwa AFTER INSERT OR UPDATE ON public.link_dicom_series_keyword FOR EACH ROW EXECUTE PROCEDURE public.trigger_fct_taiu_ldskw_update_ldkwa();


--
-- Name: link_generic_file_keyword taiu_lgfkw_update_ldkwa; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER taiu_lgfkw_update_ldkwa AFTER INSERT OR UPDATE ON public.link_generic_file_keyword FOR EACH ROW EXECUTE PROCEDURE public.trigger_fct_taiu_lgfkw_update_ldkwa();


--
-- Name: ar_file tau_ar_file; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER tau_ar_file AFTER UPDATE ON public.ar_file FOR EACH ROW EXECUTE PROCEDURE public.trigger_fct_tau_ar_file();


--
-- Name: fsnode_archive_object tau_fsnode_archive_object; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER tau_fsnode_archive_object AFTER UPDATE ON public.fsnode_archive_object FOR EACH ROW EXECUTE PROCEDURE public.trigger_fct_tau_fsnode_archive_object();


--
-- Name: link_tbl_node_tbl_item_version_fsnode_archive_object tau_link_tn_tiv_fao; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER tau_link_tn_tiv_fao AFTER UPDATE ON public.link_tbl_node_tbl_item_version_fsnode_archive_object FOR EACH ROW EXECUTE PROCEDURE public.trigger_fct_tau_link_tn_tiv_fao();


--
-- Name: tbl_item_version tau_tbl_item_version; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER tau_tbl_item_version AFTER UPDATE ON public.tbl_item_version FOR EACH ROW EXECUTE PROCEDURE public.trigger_fct_tau_tbl_item_version();


--
-- Name: ar_file tbd_ar_file; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER tbd_ar_file BEFORE DELETE ON public.ar_file FOR EACH ROW EXECUTE PROCEDURE public.trigger_fct_tbd_ar_file();


--
-- Name: fsnode_archive_object tbd_fsnode_archive_object; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER tbd_fsnode_archive_object BEFORE DELETE ON public.fsnode_archive_object FOR EACH ROW EXECUTE PROCEDURE public.trigger_fct_tbd_fsnode_archive_object();


--
-- Name: link_dicom_image_keyword tbd_ldikw_update_ldkwa; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER tbd_ldikw_update_ldkwa BEFORE DELETE ON public.link_dicom_image_keyword FOR EACH ROW EXECUTE PROCEDURE public.trigger_fct_tbd_ldikw_update_ldkwa();


--
-- Name: link_document_keyword tbd_ldkw_update_ldkwa; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER tbd_ldkw_update_ldkwa BEFORE DELETE ON public.link_document_keyword FOR EACH ROW EXECUTE PROCEDURE public.trigger_fct_tbd_ldkw_update_ldkwa();


--
-- Name: link_dicom_series_keyword tbd_ldskw_update_ldkwa; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER tbd_ldskw_update_ldkwa BEFORE DELETE ON public.link_dicom_series_keyword FOR EACH ROW EXECUTE PROCEDURE public.trigger_fct_tbd_ldskw_update_ldkwa();


--
-- Name: link_generic_file_keyword tbd_lgfkw_update_ldkwa; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER tbd_lgfkw_update_ldkwa BEFORE DELETE ON public.link_generic_file_keyword FOR EACH ROW EXECUTE PROCEDURE public.trigger_fct_tbd_lgfkw_update_ldkwa();


--
-- Name: link_tbl_node_tbl_item_version_fsnode_archive_object tbd_link_tn_tiv_fao; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER tbd_link_tn_tiv_fao BEFORE DELETE ON public.link_tbl_node_tbl_item_version_fsnode_archive_object FOR EACH ROW EXECUTE PROCEDURE public.trigger_fct_tbd_link_tn_tiv_fao();


--
-- Name: tbl_item_version tbd_tbl_item_version; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER tbd_tbl_item_version BEFORE DELETE ON public.tbl_item_version FOR EACH ROW EXECUTE PROCEDURE public.trigger_fct_tbd_tbl_item_version();


--
-- Name: extension tbiu_extension_lower_abbr; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER tbiu_extension_lower_abbr BEFORE INSERT OR UPDATE ON public.extension FOR EACH ROW EXECUTE PROCEDURE public.trigger_fct_tbiu_extension_lower_abbr();


--
-- Name: generic_file tbiu_generic_file_lower_ext; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER tbiu_generic_file_lower_ext BEFORE INSERT OR UPDATE ON public.generic_file FOR EACH ROW EXECUTE PROCEDURE public.trigger_fct_tbiu_generic_file_lower_ext();


--
-- Name: order_entry tbiu_order_e_upper; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER tbiu_order_e_upper BEFORE INSERT OR UPDATE ON public.order_entry FOR EACH ROW EXECUTE PROCEDURE public.trigger_fct_tbiu_order_e_upper();


--
-- Name: order_root tbiu_order_r_upper; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER tbiu_order_r_upper BEFORE INSERT OR UPDATE ON public.order_root FOR EACH ROW EXECUTE PROCEDURE public.trigger_fct_tbiu_order_r_upper();


--
-- Name: orderer tbiu_orderer_upper; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER tbiu_orderer_upper BEFORE INSERT OR UPDATE ON public.orderer FOR EACH ROW EXECUTE PROCEDURE public.trigger_fct_tbiu_orderer_upper();


--
-- Name: orgunit tbiu_orgunit_upper; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER tbiu_orgunit_upper BEFORE INSERT OR UPDATE ON public.orgunit FOR EACH ROW EXECUTE PROCEDURE public.trigger_fct_tbiu_orgunit_upper();


--
-- Name: document vip_document_default; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER vip_document_default BEFORE INSERT ON public.document FOR EACH ROW EXECUTE PROCEDURE public.vip_document_default();


--
-- Name: document vip_document_reassign; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER vip_document_reassign BEFORE UPDATE ON public.document FOR EACH ROW EXECUTE PROCEDURE public.vip_document_reassign();


--
-- Name: link_patient_visit vip_lpvinsert_to_visit; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER vip_lpvinsert_to_visit AFTER INSERT ON public.link_patient_visit FOR EACH ROW EXECUTE PROCEDURE public.vip_lpvinsert_to_visit();


--
-- Name: link_patient_visit vip_lpvupdate_to_visit; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER vip_lpvupdate_to_visit AFTER UPDATE ON public.link_patient_visit FOR EACH ROW EXECUTE PROCEDURE public.vip_lpvupdate_to_visit();


--
-- Name: patient vip_patient_to_visits_and_documents; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER vip_patient_to_visits_and_documents AFTER UPDATE ON public.patient FOR EACH ROW EXECUTE PROCEDURE public.vip_patient_to_visits_and_documents();


--
-- Name: visit vip_visit_to_documents; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER vip_visit_to_documents AFTER UPDATE ON public.visit FOR EACH ROW EXECUTE PROCEDURE public.vip_visit_to_documents();


--
-- Name: audit_event_property aep_audit_event_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_event_property
    ADD CONSTRAINT aep_audit_event_fk FOREIGN KEY (audit_event_fk) REFERENCES public.audit_event(id);


--
-- Name: audit_event_property aep_audit_key_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_event_property
    ADD CONSTRAINT aep_audit_key_fk FOREIGN KEY (key_fk) REFERENCES public.audit_event_property_key(id);


--
-- Name: aet aet_modality_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.aet
    ADD CONSTRAINT aet_modality_fk FOREIGN KEY (modality_fk) REFERENCES public.modality(id);


--
-- Name: aet aet_orgu_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.aet
    ADD CONSTRAINT aet_orgu_fk FOREIGN KEY (orgunit_fk) REFERENCES public.orgunit(id);


--
-- Name: aet aet_pacs_user_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.aet
    ADD CONSTRAINT aet_pacs_user_fk FOREIGN KEY (pacs_user_fk) REFERENCES public.pacs_user(id) ON DELETE SET NULL;


--
-- Name: aet aet_receiving_host_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.aet
    ADD CONSTRAINT aet_receiving_host_fk FOREIGN KEY (receiving_host_fk) REFERENCES public.host(id);


--
-- Name: annotation annotation_dicom_image_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.annotation
    ADD CONSTRAINT annotation_dicom_image_fk FOREIGN KEY (dicom_image_fk) REFERENCES public.dicom_image(id) ON DELETE CASCADE;


--
-- Name: annotation annotation_document_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.annotation
    ADD CONSTRAINT annotation_document_fk FOREIGN KEY (document_fk) REFERENCES public.document(id) ON DELETE CASCADE;


--
-- Name: annotation annotation_generic_file_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.annotation
    ADD CONSTRAINT annotation_generic_file_fk FOREIGN KEY (generic_file_fk) REFERENCES public.generic_file(id) ON DELETE CASCADE;


--
-- Name: ar_link_archive_object_file arlink_ararchiveobject_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ar_link_archive_object_file
    ADD CONSTRAINT arlink_ararchiveobject_fk FOREIGN KEY (ar_archive_object_fk) REFERENCES public.ar_archive_object(id);


--
-- Name: ar_link_archive_object_file arlink_arfile_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ar_link_archive_object_file
    ADD CONSTRAINT arlink_arfile_fk FOREIGN KEY (ar_file_fk) REFERENCES public.ar_file(id);


--
-- Name: audit_record_source audit_record_source_audit_record_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_record_source
    ADD CONSTRAINT audit_record_source_audit_record_fk FOREIGN KEY (audit_record_fk) REFERENCES public.audit_record(id) ON DELETE CASCADE;


--
-- Name: config_entry_base cfg_entr_bas_cfg_entry_desc_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.config_entry_base
    ADD CONSTRAINT cfg_entr_bas_cfg_entry_desc_fk FOREIGN KEY (config_entry_description_fk) REFERENCES public.config_entry_description(id) ON DELETE CASCADE;


--
-- Name: config_entry_base cfg_entr_bas_product_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.config_entry_base
    ADD CONSTRAINT cfg_entr_bas_product_fk FOREIGN KEY (product_fk) REFERENCES public.product(id) ON DELETE CASCADE;


--
-- Name: config_entry_description cfg_entry_desc_sec_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.config_entry_description
    ADD CONSTRAINT cfg_entry_desc_sec_fk FOREIGN KEY (config_section_description_fk) REFERENCES public.config_section_description(id) ON DELETE CASCADE;


--
-- Name: aet characterset_characterset_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.aet
    ADD CONSTRAINT characterset_characterset_fk FOREIGN KEY (characterset_fk) REFERENCES public.characterset(name);


--
-- Name: default_config default_cfg_entry_base_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.default_config
    ADD CONSTRAINT default_cfg_entry_base_fk FOREIGN KEY (config_entry_base_fk) REFERENCES public.config_entry_base(id) ON DELETE CASCADE;


--
-- Name: diagnostic_report diagnostic_report_generic_file_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.diagnostic_report
    ADD CONSTRAINT diagnostic_report_generic_file_fk FOREIGN KEY (generic_file_fk) REFERENCES public.generic_file(id) ON DELETE CASCADE;


--
-- Name: dicom_image dicimg_dicser_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dicom_image
    ADD CONSTRAINT dicimg_dicser_fk FOREIGN KEY (dicom_series_fk) REFERENCES public.dicom_series(id);


--
-- Name: dicom_image dicimg_modi_del_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dicom_image
    ADD CONSTRAINT dicimg_modi_del_fk FOREIGN KEY (deleted_by_fk) REFERENCES public.modifier(id);


--
-- Name: dicom_image dicimg_modi_ins_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dicom_image
    ADD CONSTRAINT dicimg_modi_ins_fk FOREIGN KEY (inserted_by_fk) REFERENCES public.modifier(id);


--
-- Name: dicom_image dicimg_modi_mod_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dicom_image
    ADD CONSTRAINT dicimg_modi_mod_fk FOREIGN KEY (last_modified_by_fk) REFERENCES public.modifier(id);


--
-- Name: dicom_mail_recipient dicom_mail_internal_recipient_orgunit_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dicom_mail_recipient
    ADD CONSTRAINT dicom_mail_internal_recipient_orgunit_fk FOREIGN KEY (orgunit_fk) REFERENCES public.orgunit(id);


--
-- Name: dicom_series dicser_dicstud_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dicom_series
    ADD CONSTRAINT dicser_dicstud_fk FOREIGN KEY (dicom_study_fk) REFERENCES public.dicom_study(document_fk);


--
-- Name: dicom_series dicser_modal_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dicom_series
    ADD CONSTRAINT dicser_modal_fk FOREIGN KEY (modality_fk) REFERENCES public.modality(id);


--
-- Name: dicom_series dicser_modi_del_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dicom_series
    ADD CONSTRAINT dicser_modi_del_fk FOREIGN KEY (deleted_by_fk) REFERENCES public.modifier(id);


--
-- Name: dicom_series dicser_modi_ins_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dicom_series
    ADD CONSTRAINT dicser_modi_ins_fk FOREIGN KEY (inserted_by_fk) REFERENCES public.modifier(id);


--
-- Name: dicom_series dicser_modi_mod_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dicom_series
    ADD CONSTRAINT dicser_modi_mod_fk FOREIGN KEY (last_modified_by_fk) REFERENCES public.modifier(id);


--
-- Name: dicom_study dicstud_doc_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dicom_study
    ADD CONSTRAINT dicstud_doc_fk FOREIGN KEY (document_fk) REFERENCES public.document(id);


--
-- Name: dicom_study dicstud_mod_del_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dicom_study
    ADD CONSTRAINT dicstud_mod_del_fk FOREIGN KEY (deleted_by_fk) REFERENCES public.modifier(id);


--
-- Name: dicom_study dicstud_mod_ins_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dicom_study
    ADD CONSTRAINT dicstud_mod_ins_fk FOREIGN KEY (inserted_by_fk) REFERENCES public.modifier(id);


--
-- Name: dicom_study dicstud_mod_mod_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dicom_study
    ADD CONSTRAINT dicstud_mod_mod_fk FOREIGN KEY (last_modified_by_fk) REFERENCES public.modifier(id);


--
-- Name: document doc_doctype_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.document
    ADD CONSTRAINT doc_doctype_fk FOREIGN KEY (document_type_fk) REFERENCES public.document_type(id);


--
-- Name: document doc_lpv_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.document
    ADD CONSTRAINT doc_lpv_fk FOREIGN KEY (link_patient_visit_fk) REFERENCES public.link_patient_visit(id);


--
-- Name: document doc_modi_del_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.document
    ADD CONSTRAINT doc_modi_del_fk FOREIGN KEY (deleted_by_fk) REFERENCES public.modifier(id);


--
-- Name: document doc_modi_ins_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.document
    ADD CONSTRAINT doc_modi_ins_fk FOREIGN KEY (inserted_by_fk) REFERENCES public.modifier(id);


--
-- Name: document doc_modi_mod_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.document
    ADD CONSTRAINT doc_modi_mod_fk FOREIGN KEY (last_modified_by_fk) REFERENCES public.modifier(id);


--
-- Name: document doc_orderer_orgunit_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.document
    ADD CONSTRAINT doc_orderer_orgunit_fk FOREIGN KEY (orderer) REFERENCES public.orgunit(id) ON DELETE SET NULL;


--
-- Name: document doc_producer_orgunit_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.document
    ADD CONSTRAINT doc_producer_orgunit_fk FOREIGN KEY (producer) REFERENCES public.orgunit(id);


--
-- Name: document doc_recsubtyp_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.document
    ADD CONSTRAINT doc_recsubtyp_fk FOREIGN KEY (record_subtype_fk) REFERENCES public.record_type(id);


--
-- Name: document doc_rectyp_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.document
    ADD CONSTRAINT doc_rectyp_fk FOREIGN KEY (record_type_fk) REFERENCES public.record_type(id);


--
-- Name: document_share doc_share_doc_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.document_share
    ADD CONSTRAINT doc_share_doc_fk FOREIGN KEY (document_fk) REFERENCES public.document(id) ON DELETE CASCADE;


--
-- Name: document_share doc_share_orgunit_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.document_share
    ADD CONSTRAINT doc_share_orgunit_fk FOREIGN KEY (orgunit_fk) REFERENCES public.orgunit(id);


--
-- Name: document doc_vip_ind_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.document
    ADD CONSTRAINT doc_vip_ind_fk FOREIGN KEY (vip_indicator_fk) REFERENCES public.vip_indicator(id);


--
-- Name: document_export docexp_docfk_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.document_export
    ADD CONSTRAINT docexp_docfk_fk FOREIGN KEY (document_fk) REFERENCES public.document(id) ON DELETE CASCADE;


--
-- Name: document_export docexp_unidocfk_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.document_export
    ADD CONSTRAINT docexp_unidocfk_fk FOREIGN KEY (unique_document_fk) REFERENCES public.document(id) ON DELETE CASCADE;


--
-- Name: document_class_display document_class_display_document_class_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.document_class_display
    ADD CONSTRAINT document_class_display_document_class_fk FOREIGN KEY (document_class_fk) REFERENCES public.document_class(id) ON DELETE CASCADE;


--
-- Name: document_class_display document_class_display_language_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.document_class_display
    ADD CONSTRAINT document_class_display_language_fk FOREIGN KEY (language_fk) REFERENCES public.language(id);


--
-- Name: document document_document_class_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.document
    ADD CONSTRAINT document_document_class_fk FOREIGN KEY (document_class_fk) REFERENCES public.document_class(id);


--
-- Name: document_import_helper document_import_helper_document_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.document_import_helper
    ADD CONSTRAINT document_import_helper_document_fk FOREIGN KEY (document_fk) REFERENCES public.document(id) ON DELETE CASCADE;


--
-- Name: fsnode_archive_object_summary fsnode_archive_object_summary_tbl_node_fk_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.fsnode_archive_object_summary
    ADD CONSTRAINT fsnode_archive_object_summary_tbl_node_fk_fkey FOREIGN KEY (tbl_node_fk) REFERENCES public.tbl_node(id) ON DELETE CASCADE;


--
-- Name: fsnode_injected_file fsnode_injected_file_tbl_node_fk_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.fsnode_injected_file
    ADD CONSTRAINT fsnode_injected_file_tbl_node_fk_fkey FOREIGN KEY (tbl_node_fk) REFERENCES public.tbl_node(id) ON DELETE CASCADE;


--
-- Name: generic_container gencont_doc_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.generic_container
    ADD CONSTRAINT gencont_doc_fk FOREIGN KEY (document_fk) REFERENCES public.document(id);


--
-- Name: generic_container gencont_modi_del_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.generic_container
    ADD CONSTRAINT gencont_modi_del_fk FOREIGN KEY (deleted_by_fk) REFERENCES public.modifier(id);


--
-- Name: generic_container gencont_modi_ins_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.generic_container
    ADD CONSTRAINT gencont_modi_ins_fk FOREIGN KEY (inserted_by_fk) REFERENCES public.modifier(id);


--
-- Name: generic_container gencont_modi_mod_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.generic_container
    ADD CONSTRAINT gencont_modi_mod_fk FOREIGN KEY (last_modified_by_fk) REFERENCES public.modifier(id);


--
-- Name: generic_file genfile_gencont_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

-- ALTER TABLE ONLY public.generic_file
--     ADD CONSTRAINT genfile_gencont_fk FOREIGN KEY (generic_container_fk) REFERENCES public.generic_container(document_fk);


--
-- Name: generic_file genfile_modi_del_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.generic_file
    ADD CONSTRAINT genfile_modi_del_fk FOREIGN KEY (deleted_by_fk) REFERENCES public.modifier(id);


--
-- Name: generic_file genfile_modi_ins_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.generic_file
    ADD CONSTRAINT genfile_modi_ins_fk FOREIGN KEY (inserted_by_fk) REFERENCES public.modifier(id);


--
-- Name: generic_file genfile_modi_mod_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.generic_file
    ADD CONSTRAINT genfile_modi_mod_fk FOREIGN KEY (last_modified_by_fk) REFERENCES public.modifier(id);


--
-- Name: hl7_notification hl7_notif_doc_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.hl7_notification
    ADD CONSTRAINT hl7_notif_doc_fk FOREIGN KEY (document_fk) REFERENCES public.document(id) ON DELETE CASCADE;


--
-- Name: hl7_notification hl7_notif_genfile_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.hl7_notification
    ADD CONSTRAINT hl7_notif_genfile_fk FOREIGN KEY (generic_file_fk) REFERENCES public.generic_file(id) ON DELETE CASCADE;


--
-- Name: hl7_notification hl7_notif_notifstat_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.hl7_notification
    ADD CONSTRAINT hl7_notif_notifstat_fk FOREIGN KEY (hl7_notification_status_fk) REFERENCES public.hl7_notification_status(id);


--
-- Name: host_config host_cfg_host_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.host_config
    ADD CONSTRAINT host_cfg_host_fk FOREIGN KEY (host_fk) REFERENCES public.host(id) ON DELETE CASCADE;


--
-- Name: host_config host_config_cfg_entry_base_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.host_config
    ADD CONSTRAINT host_config_cfg_entry_base_fk FOREIGN KEY (config_entry_base_fk) REFERENCES public.config_entry_base(id) ON DELETE CASCADE;


--
-- Name: host host_orgunit_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.host
    ADD CONSTRAINT host_orgunit_fk FOREIGN KEY (orgunit_fk) REFERENCES public.orgunit(id);


--
-- Name: ihe_actor ihe_actor_associated_with_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ihe_actor
    ADD CONSTRAINT ihe_actor_associated_with_fk FOREIGN KEY (associated_with_fk) REFERENCES public.ihe_actor(id);


--
-- Name: ihe_actor ihe_actor_ihe_domain_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

-- ALTER TABLE ONLY public.ihe_actor
--     ADD CONSTRAINT ihe_actor_ihe_domain_fk FOREIGN KEY (ihe_domain_fk) REFERENCES public.ihe_domain(id);


--
-- Name: ihe_endpoint ihe_endpoint_ihe_actor_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ihe_endpoint
    ADD CONSTRAINT ihe_endpoint_ihe_actor_fk FOREIGN KEY (ihe_actor_fk) REFERENCES public.ihe_actor(id) ON DELETE CASCADE;


--
-- Name: ihe_manifest ihe_manifest_dcm_img_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ihe_manifest
    ADD CONSTRAINT ihe_manifest_dcm_img_fk FOREIGN KEY (dicom_image_fk) REFERENCES public.dicom_image(id) ON DELETE CASCADE;


--
-- Name: ihe_manifest ihe_manifest_gf_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ihe_manifest
    ADD CONSTRAINT ihe_manifest_gf_fk FOREIGN KEY (generic_file_fk) REFERENCES public.generic_file(id) ON DELETE CASCADE;


--
-- Name: ihe_manifest ihe_manifest_ihe_domain_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

-- ALTER TABLE ONLY public.ihe_manifest
--     ADD CONSTRAINT ihe_manifest_ihe_domain_fk FOREIGN KEY (ihe_domain_fk) REFERENCES public.ihe_domain(id);


--
-- Name: imedone_manifest imedone_manifest_generic_file_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.imedone_manifest
    ADD CONSTRAINT imedone_manifest_generic_file_fk FOREIGN KEY (generic_file_fk) REFERENCES public.generic_file(id) ON DELETE CASCADE;


--
-- Name: item_version_security_prop item_ver_sec_pro_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.item_version_security_prop
    ADD CONSTRAINT item_ver_sec_pro_fk FOREIGN KEY (item_version_security_fk) REFERENCES public.item_version_security(id) ON DELETE CASCADE;


--
-- Name: item_version_security item_version_sec_tiv_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.item_version_security
    ADD CONSTRAINT item_version_sec_tiv_fk FOREIGN KEY (tbl_item_version_fk) REFERENCES public.tbl_item_version(id) ON DELETE CASCADE;


--
-- Name: keyword_class keyword_class_kwcgroup_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.keyword_class
    ADD CONSTRAINT keyword_class_kwcgroup_fk FOREIGN KEY (keyword_class_group_fk) REFERENCES public.keyword_class_group(name);


--
-- Name: keyword_class keyword_class_type_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.keyword_class
    ADD CONSTRAINT keyword_class_type_fk FOREIGN KEY (type_fk) REFERENCES public.keyword_class_type(id);


--
-- Name: keyword_display keyword_display_keyword_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.keyword_display
    ADD CONSTRAINT keyword_display_keyword_fk FOREIGN KEY (keyword_fk) REFERENCES public.keyword(id) ON DELETE CASCADE;


--
-- Name: keyword_display keyword_display_language_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.keyword_display
    ADD CONSTRAINT keyword_display_language_fk FOREIGN KEY (language_fk) REFERENCES public.language(id);


--
-- Name: keyword_class kw_class_default_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.keyword_class
    ADD CONSTRAINT kw_class_default_fk FOREIGN KEY (default_fk) REFERENCES public.keyword(id);


--
-- Name: keyword kw_kw_class_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.keyword
    ADD CONSTRAINT kw_kw_class_fk FOREIGN KEY (keyword_class_fk) REFERENCES public.keyword_class(id);


--
-- Name: keyword kw_parent_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.keyword
    ADD CONSTRAINT kw_parent_fk FOREIGN KEY (parent_fk) REFERENCES public.keyword(id);


--
-- Name: keyword_class_constraint kwclassconstraint_kwclassfk_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.keyword_class_constraint
    ADD CONSTRAINT kwclassconstraint_kwclassfk_fk FOREIGN KEY (keyword_class_fk) REFERENCES public.keyword_class(id);


--
-- Name: keyword_class_constraint kwclassconstraint_typefk_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.keyword_class_constraint
    ADD CONSTRAINT kwclassconstraint_typefk_fk FOREIGN KEY (keyword_class_const_type_fk) REFERENCES public.keyword_class_constraint_type(name);


--
-- Name: link_extension_extension_group l_ext_ext_grp_e_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.link_extension_extension_group
    ADD CONSTRAINT l_ext_ext_grp_e_fk FOREIGN KEY (extension_fk) REFERENCES public.extension(abbreviation);


--
-- Name: link_extension_extension_group l_ext_ext_grp_eg_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.link_extension_extension_group
    ADD CONSTRAINT l_ext_ext_grp_eg_fk FOREIGN KEY (extension_group_fk) REFERENCES public.extension_group(name);


--
-- Name: link_aet_aet laet_cl_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.link_aet_aet
    ADD CONSTRAINT laet_cl_fk FOREIGN KEY (client_aet_fk) REFERENCES public.aet(id);


--
-- Name: link_aet_aet laet_modgr_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.link_aet_aet
    ADD CONSTRAINT laet_modgr_fk FOREIGN KEY (modgrp_aet_fk) REFERENCES public.aet(id);


--
-- Name: link_aet_host laethost_aet_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.link_aet_host
    ADD CONSTRAINT laethost_aet_fk FOREIGN KEY (aet_fk) REFERENCES public.aet(id);


--
-- Name: link_aet_host laethost_host_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.link_aet_host
    ADD CONSTRAINT laethost_host_fk FOREIGN KEY (host_fk) REFERENCES public.host(id);


--
-- Name: link_document_class_keyword ldckw_document_class_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.link_document_class_keyword
    ADD CONSTRAINT ldckw_document_class_fk FOREIGN KEY (document_class_fk) REFERENCES public.document_class(id) ON DELETE CASCADE;


--
-- Name: link_document_class_keyword ldckw_keyword_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.link_document_class_keyword
    ADD CONSTRAINT ldckw_keyword_fk FOREIGN KEY (keyword_fk) REFERENCES public.keyword(id);


--
-- Name: link_dicom_image_keyword ldikw_di_fk_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.link_dicom_image_keyword
    ADD CONSTRAINT ldikw_di_fk_fk FOREIGN KEY (dicom_image_fk) REFERENCES public.dicom_image(id) ON DELETE CASCADE;


--
-- Name: link_dicom_image_keyword ldikw_keyword_fk_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.link_dicom_image_keyword
    ADD CONSTRAINT ldikw_keyword_fk_fk FOREIGN KEY (keyword_fk) REFERENCES public.keyword(id);


--
-- Name: link_document_keyword ldkw_document_fk_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.link_document_keyword
    ADD CONSTRAINT ldkw_document_fk_fk FOREIGN KEY (document_fk) REFERENCES public.document(id) ON DELETE CASCADE;


--
-- Name: link_document_keyword ldkw_keyword_fk_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.link_document_keyword
    ADD CONSTRAINT ldkw_keyword_fk_fk FOREIGN KEY (keyword_fk) REFERENCES public.keyword(id);


--
-- Name: link_document_keyword_all ldkwa_document_fk_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.link_document_keyword_all
    ADD CONSTRAINT ldkwa_document_fk_fk FOREIGN KEY (document_fk) REFERENCES public.document(id) ON DELETE CASCADE;


--
-- Name: link_document_keyword_all ldkwa_keyword_fk_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.link_document_keyword_all
    ADD CONSTRAINT ldkwa_keyword_fk_fk FOREIGN KEY (keyword_fk) REFERENCES public.keyword(id);


--
-- Name: link_dicom_series_keyword ldskw_ds_fk_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.link_dicom_series_keyword
    ADD CONSTRAINT ldskw_ds_fk_fk FOREIGN KEY (dicom_series_fk) REFERENCES public.dicom_series(id) ON DELETE CASCADE;


--
-- Name: link_dicom_series_keyword ldskw_keyword_fk_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.link_dicom_series_keyword
    ADD CONSTRAINT ldskw_keyword_fk_fk FOREIGN KEY (keyword_fk) REFERENCES public.keyword(id);


--
-- Name: link_generic_file_keyword lgfkw_gf_fk_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.link_generic_file_keyword
    ADD CONSTRAINT lgfkw_gf_fk_fk FOREIGN KEY (generic_file_fk) REFERENCES public.generic_file(id) ON DELETE CASCADE;


--
-- Name: link_generic_file_keyword lgfkw_keyword_fk_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.link_generic_file_keyword
    ADD CONSTRAINT lgfkw_keyword_fk_fk FOREIGN KEY (keyword_fk) REFERENCES public.keyword(id);


--
-- Name: link_aet_ignored_sop_class link_aet_ignored_sop_class_aet_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.link_aet_ignored_sop_class
    ADD CONSTRAINT link_aet_ignored_sop_class_aet_fk FOREIGN KEY (aet_fk) REFERENCES public.aet(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: link_aet_ignored_sop_class link_aet_ignored_sop_class_sop_class_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.link_aet_ignored_sop_class
    ADD CONSTRAINT link_aet_ignored_sop_class_sop_class_fk FOREIGN KEY (sop_class_fk) REFERENCES public.sop_class(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: link_aet_transfer_syntax link_aet_transfer_syntax_aet_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.link_aet_transfer_syntax
    ADD CONSTRAINT link_aet_transfer_syntax_aet_fk FOREIGN KEY (aet_fk) REFERENCES public.aet(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: link_aet_transfer_syntax link_aet_transfer_syntax_transfer_syntax_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.link_aet_transfer_syntax
    ADD CONSTRAINT link_aet_transfer_syntax_transfer_syntax_fk FOREIGN KEY (transfer_syntax_fk) REFERENCES public.transfer_syntax(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: link_diagnostic_report_fhir_identifier link_diagnostic_report_fhir_identifier_diagnostic_report_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.link_diagnostic_report_fhir_identifier
    ADD CONSTRAINT link_diagnostic_report_fhir_identifier_diagnostic_report_fk FOREIGN KEY (diagnostic_report_fk) REFERENCES public.diagnostic_report(id) ON DELETE CASCADE;


--
-- Name: link_diagnostic_report_fhir_identifier link_diagnostic_report_fhir_identifier_fhir_identifier_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.link_diagnostic_report_fhir_identifier
    ADD CONSTRAINT link_diagnostic_report_fhir_identifier_fhir_identifier_fk FOREIGN KEY (fhir_identifier_fk) REFERENCES public.fhir_identifier(id);


--
-- Name: link_dicom_image_image_marker link_dicom_image_image_marker_dicom_image_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.link_dicom_image_image_marker
    ADD CONSTRAINT link_dicom_image_image_marker_dicom_image_fk FOREIGN KEY (dicom_image_fk) REFERENCES public.dicom_image(id) ON DELETE CASCADE;


--
-- Name: link_dicom_image_image_marker link_dicom_image_image_marker_image_marker_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.link_dicom_image_image_marker
    ADD CONSTRAINT link_dicom_image_image_marker_image_marker_fk FOREIGN KEY (image_marker_fk) REFERENCES public.image_marker(id);


--
-- Name: link_document_class_group link_document_class_group_document_class_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.link_document_class_group
    ADD CONSTRAINT link_document_class_group_document_class_fk FOREIGN KEY (document_class_fk) REFERENCES public.document_class(id) ON DELETE CASCADE;


--
-- Name: link_document_class_group link_document_class_group_document_class_group_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.link_document_class_group
    ADD CONSTRAINT link_document_class_group_document_class_group_fk FOREIGN KEY (document_class_group_fk) REFERENCES public.document_class_group(id) ON DELETE CASCADE;


--
-- Name: link_document_document_marker link_document_document_marker_document_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.link_document_document_marker
    ADD CONSTRAINT link_document_document_marker_document_fk FOREIGN KEY (document_fk) REFERENCES public.document(id) ON DELETE CASCADE;


--
-- Name: link_document_document_marker link_document_document_marker_document_marker_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.link_document_document_marker
    ADD CONSTRAINT link_document_document_marker_document_marker_fk FOREIGN KEY (document_marker_fk) REFERENCES public.document_marker(id);


--
-- Name: link_generic_file_image_marker link_generic_file_image_marker_generic_file_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.link_generic_file_image_marker
    ADD CONSTRAINT link_generic_file_image_marker_generic_file_fk FOREIGN KEY (generic_file_fk) REFERENCES public.generic_file(id) ON DELETE CASCADE;


--
-- Name: link_generic_file_image_marker link_generic_file_image_marker_image_marker_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.link_generic_file_image_marker
    ADD CONSTRAINT link_generic_file_image_marker_image_marker_fk FOREIGN KEY (image_marker_fk) REFERENCES public.image_marker(id);


--
-- Name: link_medication_administration_fhir_identifier link_medication_administration_fhir_identifier_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.link_medication_administration_fhir_identifier
    ADD CONSTRAINT link_medication_administration_fhir_identifier_fk FOREIGN KEY (fhir_identifier_fk) REFERENCES public.fhir_identifier(id);


--
-- Name: link_medication_administration_fhir_identifier link_medication_administration_medication_administration_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.link_medication_administration_fhir_identifier
    ADD CONSTRAINT link_medication_administration_medication_administration_fk FOREIGN KEY (medication_administration_fk) REFERENCES public.medication_administration(id) ON DELETE CASCADE;


--
-- Name: link_mpps_info_o_procedure link_mpps_info_o_procedure_mpps_info_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.link_mpps_info_o_procedure
    ADD CONSTRAINT link_mpps_info_o_procedure_mpps_info_fk FOREIGN KEY (mpps_info_fk) REFERENCES public.mpps_info(id) ON DELETE CASCADE;


--
-- Name: link_mpps_info_o_procedure link_mpps_info_o_procedure_o_procedure_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.link_mpps_info_o_procedure
    ADD CONSTRAINT link_mpps_info_o_procedure_o_procedure_fk FOREIGN KEY (o_procedure_fk) REFERENCES public.o_procedure(id) ON DELETE CASCADE;


--
-- Name: link_observation_fhir_identifier link_observation_fhir_identifier_fhir_identifier_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.link_observation_fhir_identifier
    ADD CONSTRAINT link_observation_fhir_identifier_fhir_identifier_fk FOREIGN KEY (fhir_identifier_fk) REFERENCES public.fhir_identifier(id);


--
-- Name: link_observation_fhir_identifier link_observation_fhir_identifier_observation_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.link_observation_fhir_identifier
    ADD CONSTRAINT link_observation_fhir_identifier_observation_fk FOREIGN KEY (observation_fk) REFERENCES public.observation(id) ON DELETE CASCADE;


--
-- Name: link_presentation_document link_presentation_document_document_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.link_presentation_document
    ADD CONSTRAINT link_presentation_document_document_fk FOREIGN KEY (document_fk) REFERENCES public.document(id) ON DELETE CASCADE;


--
-- Name: link_presentation_document link_presentation_document_presentation_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.link_presentation_document
    ADD CONSTRAINT link_presentation_document_presentation_fk FOREIGN KEY (presentation_fk) REFERENCES public.presentation(id) ON DELETE CASCADE;


--
-- Name: link_report_item_version_security link_report_item_version_security_item_version_security_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.link_report_item_version_security
    ADD CONSTRAINT link_report_item_version_security_item_version_security_fk FOREIGN KEY (item_version_security_fk) REFERENCES public.item_version_security(id) ON DELETE CASCADE;


--
-- Name: link_report_item_version_security link_report_item_version_security_report_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.link_report_item_version_security
    ADD CONSTRAINT link_report_item_version_security_report_fk FOREIGN KEY (report_fk) REFERENCES public.document(id) ON DELETE CASCADE;


--
-- Name: link_role_pacs_user link_role_pacs_user_role_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.link_role_pacs_user
    ADD CONSTRAINT link_role_pacs_user_role_fk FOREIGN KEY (role_fk) REFERENCES public.role(name) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: link_role_permission link_role_perm_permission_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.link_role_permission
    ADD CONSTRAINT link_role_perm_permission_fk FOREIGN KEY (permission_fk) REFERENCES public.permission(name) ON DELETE CASCADE;


--
-- Name: link_role_permission link_role_perm_role_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.link_role_permission
    ADD CONSTRAINT link_role_perm_role_fk FOREIGN KEY (role_fk) REFERENCES public.role(name) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: link_role_pacs_user link_role_user_user_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.link_role_pacs_user
    ADD CONSTRAINT link_role_user_user_fk FOREIGN KEY (pacs_user_fk) REFERENCES public.pacs_user(id) ON DELETE CASCADE;


--
-- Name: link_snapshot_document link_snapshot_document_document_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.link_snapshot_document
    ADD CONSTRAINT link_snapshot_document_document_fk FOREIGN KEY (document_fk) REFERENCES public.document(id) ON DELETE CASCADE;


--
-- Name: link_snapshot_document link_snapshot_document_snapshot_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.link_snapshot_document
    ADD CONSTRAINT link_snapshot_document_snapshot_fk FOREIGN KEY (snapshot_fk) REFERENCES public.snapshot(id) ON DELETE CASCADE;


--
-- Name: link_storage_rule_tbl_node link_storage_rule_tbl_node_node_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.link_storage_rule_tbl_node
    ADD CONSTRAINT link_storage_rule_tbl_node_node_fk FOREIGN KEY (tbl_node_fk) REFERENCES public.tbl_node(id);


--
-- Name: link_storage_rule_tbl_node link_storage_rule_tbl_node_rule_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.link_storage_rule_tbl_node
    ADD CONSTRAINT link_storage_rule_tbl_node_rule_fk FOREIGN KEY (storage_rule_fk) REFERENCES public.storage_rule(id) ON DELETE CASCADE;


--
-- Name: link_tbl_node_tbl_item_version_fsnode_archive_object link_tbl_node_tbl_item_version_fs_fsnode_archive_object_fk_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.link_tbl_node_tbl_item_version_fsnode_archive_object
    ADD CONSTRAINT link_tbl_node_tbl_item_version_fs_fsnode_archive_object_fk_fkey FOREIGN KEY (fsnode_archive_object_fk) REFERENCES public.fsnode_archive_object(id) ON DELETE CASCADE;


--
-- Name: link_tbl_node_tbl_item_version_fsnode_archive_object link_tbl_node_tbl_item_version_fsnode__tbl_item_version_fk_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.link_tbl_node_tbl_item_version_fsnode_archive_object
    ADD CONSTRAINT link_tbl_node_tbl_item_version_fsnode__tbl_item_version_fk_fkey FOREIGN KEY (tbl_item_version_fk) REFERENCES public.tbl_item_version(id) ON DELETE CASCADE;


--
-- Name: link_tbl_node_tbl_item_version_fsnode_archive_object link_tbl_node_tbl_item_version_fsnode_archive__tbl_node_fk_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.link_tbl_node_tbl_item_version_fsnode_archive_object
    ADD CONSTRAINT link_tbl_node_tbl_item_version_fsnode_archive__tbl_node_fk_fkey FOREIGN KEY (tbl_node_fk) REFERENCES public.tbl_node(id) ON DELETE CASCADE;


--
-- Name: link_keyword_class_group_level lkcl_keyword_class_group_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.link_keyword_class_group_level
    ADD CONSTRAINT lkcl_keyword_class_group_fk FOREIGN KEY (keyword_class_group_fk) REFERENCES public.keyword_class_group(name);


--
-- Name: link_keyword_class_group_level lkcl_keyword_level_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.link_keyword_class_group_level
    ADD CONSTRAINT lkcl_keyword_level_fk FOREIGN KEY (keyword_level_fk) REFERENCES public.keyword_level(name);


--
-- Name: link_o_procedure_keyword lopkw_keyword_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.link_o_procedure_keyword
    ADD CONSTRAINT lopkw_keyword_fk FOREIGN KEY (keyword_fk) REFERENCES public.keyword(id);


--
-- Name: link_o_procedure_keyword lopkw_o_procedure_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.link_o_procedure_keyword
    ADD CONSTRAINT lopkw_o_procedure_fk FOREIGN KEY (o_procedure_fk) REFERENCES public.o_procedure(id) ON DELETE CASCADE;


--
-- Name: link_patient_visit lpv_pat_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.link_patient_visit
    ADD CONSTRAINT lpv_pat_fk FOREIGN KEY (patient_fk) REFERENCES public.patient(id);


--
-- Name: link_patient_visit lpv_visit_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.link_patient_visit
    ADD CONSTRAINT lpv_visit_fk FOREIGN KEY (visit_fk) REFERENCES public.visit(id);


--
-- Name: medication_administration medication_administration_generic_file_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.medication_administration
    ADD CONSTRAINT medication_administration_generic_file_fk FOREIGN KEY (generic_file_fk) REFERENCES public.generic_file(id) ON DELETE CASCADE;


--
-- Name: mpps_image_info mpps_image_info_mpps_series_info_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mpps_image_info
    ADD CONSTRAINT mpps_image_info_mpps_series_info_fk FOREIGN KEY (mpps_series_info_fk) REFERENCES public.mpps_series_info(id) ON DELETE CASCADE;


--
-- Name: mpps_series_info mpps_series_info_mpps_info_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mpps_series_info
    ADD CONSTRAINT mpps_series_info_mpps_info_fk FOREIGN KEY (mpps_info_fk) REFERENCES public.mpps_info(id) ON DELETE CASCADE;


--
-- Name: node_info nodinf_tblnod_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.node_info
    ADD CONSTRAINT nodinf_tblnod_fk FOREIGN KEY (tbl_node_fk) REFERENCES public.tbl_node(id);


--
-- Name: notification notif_docfk_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification
    ADD CONSTRAINT notif_docfk_fk FOREIGN KEY (document_fk) REFERENCES public.document(id) ON DELETE CASCADE;


--
-- Name: notification notif_eventfk_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification
    ADD CONSTRAINT notif_eventfk_fk FOREIGN KEY (notification_event_fk) REFERENCES public.notification_event(id);


--
-- Name: notification_type notiftyp_notifeve_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_type
    ADD CONSTRAINT notiftyp_notifeve_fk FOREIGN KEY (notification_event_fk) REFERENCES public.notification_event(id);


--
-- Name: o_procedure o_procedure_document_class_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.o_procedure
    ADD CONSTRAINT o_procedure_document_class_fk FOREIGN KEY (document_class_fk) REFERENCES public.document_class(id);


--
-- Name: o_procedure o_procedure_link_patient_visit_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.o_procedure
    ADD CONSTRAINT o_procedure_link_patient_visit_fk FOREIGN KEY (link_patient_visit_fk) REFERENCES public.link_patient_visit(id);


--
-- Name: o_procedure o_procedure_modality_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.o_procedure
    ADD CONSTRAINT o_procedure_modality_fk FOREIGN KEY (modality_fk) REFERENCES public.modality(id);


--
-- Name: o_procedure o_procedure_orderer_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.o_procedure
    ADD CONSTRAINT o_procedure_orderer_fk FOREIGN KEY (orderer_fk) REFERENCES public.orgunit(id);


--
-- Name: o_procedure o_procedure_scheduled_station_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.o_procedure
    ADD CONSTRAINT o_procedure_scheduled_station_fk FOREIGN KEY (scheduled_station_fk) REFERENCES public.aet(id);


--
-- Name: observation observation_diagnostic_report_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.observation
    ADD CONSTRAINT observation_diagnostic_report_fk FOREIGN KEY (diagnostic_report_fk) REFERENCES public.diagnostic_report(id) ON DELETE CASCADE;


--
-- Name: observation observation_generic_file_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.observation
    ADD CONSTRAINT observation_generic_file_fk FOREIGN KEY (generic_file_fk) REFERENCES public.generic_file(id) ON DELETE CASCADE;


--
-- Name: order_additional_info order_a_i_order_e_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_additional_info
    ADD CONSTRAINT order_a_i_order_e_fk FOREIGN KEY (order_entry_fk) REFERENCES public.order_entry(id) ON DELETE CASCADE;


--
-- Name: order_entry order_e_aet_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_entry
    ADD CONSTRAINT order_e_aet_fk FOREIGN KEY (service_location_fk) REFERENCES public.aet(name);


--
-- Name: order_entry_history order_e_h_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_entry_history
    ADD CONSTRAINT order_e_h_fk FOREIGN KEY (order_entry_fk) REFERENCES public.order_entry(id) ON DELETE CASCADE;


--
-- Name: order_entry_history order_e_h_modi_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_entry_history
    ADD CONSTRAINT order_e_h_modi_fk FOREIGN KEY (modifier_fk) REFERENCES public.modifier(id);


--
-- Name: order_entry_history order_e_h_status_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_entry_history
    ADD CONSTRAINT order_e_h_status_fk FOREIGN KEY (order_status_fk) REFERENCES public.order_status(code);


--
-- Name: order_entry order_e_modality_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_entry
    ADD CONSTRAINT order_e_modality_fk FOREIGN KEY (modality_fk) REFERENCES public.modality(id);


--
-- Name: order_entry order_e_o_e_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_entry
    ADD CONSTRAINT order_e_o_e_fk FOREIGN KEY (order_entry_fk) REFERENCES public.order_entry(id) ON DELETE CASCADE;


--
-- Name: order_entry order_e_o_s_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_entry
    ADD CONSTRAINT order_e_o_s_fk FOREIGN KEY (order_status_fk) REFERENCES public.order_status(code);


--
-- Name: order_root order_root_lpv_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_root
    ADD CONSTRAINT order_root_lpv_fk FOREIGN KEY (link_patient_visit_fk) REFERENCES public.link_patient_visit(id);


--
-- Name: order_root order_root_order_e_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_root
    ADD CONSTRAINT order_root_order_e_fk FOREIGN KEY (order_entry_fk) REFERENCES public.order_entry(id);


--
-- Name: orderer orderer_order_e_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orderer
    ADD CONSTRAINT orderer_order_e_fk FOREIGN KEY (order_entry_fk) REFERENCES public.order_entry(id) ON DELETE CASCADE;


--
-- Name: orderer orderer_ou_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orderer
    ADD CONSTRAINT orderer_ou_fk FOREIGN KEY (orgunit_fk) REFERENCES public.orgunit(abk) ON DELETE SET NULL;


--
-- Name: orderer orderer_type_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orderer
    ADD CONSTRAINT orderer_type_fk FOREIGN KEY (type_fk) REFERENCES public.orderer_type(name);


--
-- Name: orgunit orgu_orgu_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orgunit
    ADD CONSTRAINT orgu_orgu_fk FOREIGN KEY (orgunit_fk) REFERENCES public.orgunit(id);


--
-- Name: orgunit_config orgunit_cfg_cfg_entry_base_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orgunit_config
    ADD CONSTRAINT orgunit_cfg_cfg_entry_base_fk FOREIGN KEY (config_entry_base_fk) REFERENCES public.config_entry_base(id) ON DELETE CASCADE;


--
-- Name: orgunit_config orgunit_cfg_orgunit_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orgunit_config
    ADD CONSTRAINT orgunit_cfg_orgunit_fk FOREIGN KEY (orgunit_fk) REFERENCES public.orgunit(id) ON DELETE CASCADE;


--
-- Name: orgunit orgunit_insertby_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orgunit
    ADD CONSTRAINT orgunit_insertby_fk FOREIGN KEY (inserted_by_fk) REFERENCES public.modifier(id);


--
-- Name: orgunit orgunit_modifby_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orgunit
    ADD CONSTRAINT orgunit_modifby_fk FOREIGN KEY (last_modified_by_fk) REFERENCES public.modifier(id);


--
-- Name: pacs_user_attribute pacs_user_attribute_pacs_user_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pacs_user_attribute
    ADD CONSTRAINT pacs_user_attribute_pacs_user_fk FOREIGN KEY (pacs_user_fk) REFERENCES public.pacs_user(id) ON DELETE CASCADE;


--
-- Name: user_config pacs_user_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_config
    ADD CONSTRAINT pacs_user_fk FOREIGN KEY (pacs_user_fk) REFERENCES public.pacs_user(id) ON DELETE CASCADE;


--
-- Name: pacs_user pacs_user_patient_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pacs_user
    ADD CONSTRAINT pacs_user_patient_fk FOREIGN KEY (patient_fk) REFERENCES public.patient(id);


--
-- Name: pacs_user_token pacs_user_token_pacs_user_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pacs_user_token
    ADD CONSTRAINT pacs_user_token_pacs_user_fk FOREIGN KEY (pacs_user_fk) REFERENCES public.pacs_user(id) ON DELETE CASCADE;


--
-- Name: pacs_session_parameter pacssessparam_pacssessperm_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pacs_session_parameter
    ADD CONSTRAINT pacssessparam_pacssessperm_fk FOREIGN KEY (pacs_session_permission_fk) REFERENCES public.pacs_session_permission(id) ON DELETE CASCADE;


--
-- Name: pacs_session_permission pacssessperm_pacssess_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pacs_session_permission
    ADD CONSTRAINT pacssessperm_pacssess_fk FOREIGN KEY (pacs_session_fk) REFERENCES public.pacs_session(id) ON DELETE CASCADE;


--
-- Name: pacs_session_permission pacssessperm_permission_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pacs_session_permission
    ADD CONSTRAINT pacssessperm_permission_fk FOREIGN KEY (permission_fk) REFERENCES public.permission(name) ON DELETE CASCADE;


--
-- Name: parameter parameter_lrp_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.parameter
    ADD CONSTRAINT parameter_lrp_fk FOREIGN KEY (link_role_permission_fk) REFERENCES public.link_role_permission(id) ON DELETE CASCADE;


--
-- Name: parameter parameter_ppd_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.parameter
    ADD CONSTRAINT parameter_ppd_fk FOREIGN KEY (permission_parameter_desc_fk) REFERENCES public.permission_parameter_desc(id);


--
-- Name: patient pat_modi_del_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.patient
    ADD CONSTRAINT pat_modi_del_fk FOREIGN KEY (deleted_by_fk) REFERENCES public.modifier(id);


--
-- Name: patient pat_modi_ins_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.patient
    ADD CONSTRAINT pat_modi_ins_fk FOREIGN KEY (inserted_by_fk) REFERENCES public.modifier(id);


--
-- Name: patient pat_modi_mod_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.patient
    ADD CONSTRAINT pat_modi_mod_fk FOREIGN KEY (last_modified_by_fk) REFERENCES public.modifier(id);


--
-- Name: patient pat_pat_inval_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.patient
    ADD CONSTRAINT pat_pat_inval_fk FOREIGN KEY (patient_invalidated_by_fk) REFERENCES public.patient(id);


--
-- Name: patient pat_sex_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.patient
    ADD CONSTRAINT pat_sex_fk FOREIGN KEY (sex_fk) REFERENCES public.sex(id);


--
-- Name: patient pat_vip_ind_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.patient
    ADD CONSTRAINT pat_vip_ind_fk FOREIGN KEY (vip_indicator_fk) REFERENCES public.vip_indicator(id);


--
-- Name: permission_parameter_desc perm_param_desc_perm_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.permission_parameter_desc
    ADD CONSTRAINT perm_param_desc_perm_fk FOREIGN KEY (permission_fk) REFERENCES public.permission(name) ON DELETE CASCADE;


--
-- Name: permission_parameter_desc perm_param_desc_type_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.permission_parameter_desc
    ADD CONSTRAINT perm_param_desc_type_fk FOREIGN KEY (type_fk) REFERENCES public.permission_parameter_type(name);


--
-- Name: presentation presentation_created_by_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.presentation
    ADD CONSTRAINT presentation_created_by_fk FOREIGN KEY (created_by_fk) REFERENCES public.pacs_user(id) ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: presentation presentation_orgunit_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.presentation
    ADD CONSTRAINT presentation_orgunit_fk FOREIGN KEY (orgunit_fk) REFERENCES public.orgunit(id);


--
-- Name: pacs_session psess_host_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pacs_session
    ADD CONSTRAINT psess_host_fk FOREIGN KEY (host_fk) REFERENCES public.host(id);


--
-- Name: pacs_session psess_puser_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pacs_session
    ADD CONSTRAINT psess_puser_fk FOREIGN KEY (pacs_user_fk) REFERENCES public.pacs_user(id) ON DELETE CASCADE;


--
-- Name: pacs_user puser_insertby_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pacs_user
    ADD CONSTRAINT puser_insertby_fk FOREIGN KEY (inserted_by_fk) REFERENCES public.modifier(id);


--
-- Name: pacs_user puser_modifby_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pacs_user
    ADD CONSTRAINT puser_modifby_fk FOREIGN KEY (last_modified_by_fk) REFERENCES public.modifier(id);


--
-- Name: record_type rectyp_typhint01_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.record_type
    ADD CONSTRAINT rectyp_typhint01_fk FOREIGN KEY (index01_type_hint_fk) REFERENCES public.type_hint(id);


--
-- Name: record_type rectyp_typhint02_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.record_type
    ADD CONSTRAINT rectyp_typhint02_fk FOREIGN KEY (index02_type_hint_fk) REFERENCES public.type_hint(id);


--
-- Name: record_type rectyp_typhint03_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.record_type
    ADD CONSTRAINT rectyp_typhint03_fk FOREIGN KEY (index03_type_hint_fk) REFERENCES public.type_hint(id);


--
-- Name: record_type rectyp_typhint04_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.record_type
    ADD CONSTRAINT rectyp_typhint04_fk FOREIGN KEY (index04_type_hint_fk) REFERENCES public.type_hint(id);


--
-- Name: record_type rectyp_typhint05_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.record_type
    ADD CONSTRAINT rectyp_typhint05_fk FOREIGN KEY (index05_type_hint_fk) REFERENCES public.type_hint(id);


--
-- Name: record_type rectyp_typhint06_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.record_type
    ADD CONSTRAINT rectyp_typhint06_fk FOREIGN KEY (index06_type_hint_fk) REFERENCES public.type_hint(id);


--
-- Name: record_type rectyp_typhint07_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.record_type
    ADD CONSTRAINT rectyp_typhint07_fk FOREIGN KEY (index07_type_hint_fk) REFERENCES public.type_hint(id);


--
-- Name: record_type rectyp_typhint08_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.record_type
    ADD CONSTRAINT rectyp_typhint08_fk FOREIGN KEY (index08_type_hint_fk) REFERENCES public.type_hint(id);


--
-- Name: record_type rectyp_typhint09_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.record_type
    ADD CONSTRAINT rectyp_typhint09_fk FOREIGN KEY (index09_type_hint_fk) REFERENCES public.type_hint(id);


--
-- Name: record_type rectyp_typhint10_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.record_type
    ADD CONSTRAINT rectyp_typhint10_fk FOREIGN KEY (index10_type_hint_fk) REFERENCES public.type_hint(id);


--
-- Name: record_type rectyp_typhint11_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.record_type
    ADD CONSTRAINT rectyp_typhint11_fk FOREIGN KEY (index11_type_hint_fk) REFERENCES public.type_hint(id);


--
-- Name: record_type rectyp_typhint12_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.record_type
    ADD CONSTRAINT rectyp_typhint12_fk FOREIGN KEY (index12_type_hint_fk) REFERENCES public.type_hint(id);


--
-- Name: record_type rectyp_typhint13_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.record_type
    ADD CONSTRAINT rectyp_typhint13_fk FOREIGN KEY (index13_type_hint_fk) REFERENCES public.type_hint(id);


--
-- Name: record_type rectyp_typhint14_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.record_type
    ADD CONSTRAINT rectyp_typhint14_fk FOREIGN KEY (index14_type_hint_fk) REFERENCES public.type_hint(id);


--
-- Name: record_type rectyp_typhint15_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.record_type
    ADD CONSTRAINT rectyp_typhint15_fk FOREIGN KEY (index15_type_hint_fk) REFERENCES public.type_hint(id);


--
-- Name: send_process_message send_process_message_dicom_image_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.send_process_message
    ADD CONSTRAINT send_process_message_dicom_image_fk FOREIGN KEY (dicom_image_fk) REFERENCES public.dicom_image(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: send_process_message send_process_message_generic_file_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.send_process_message
    ADD CONSTRAINT send_process_message_generic_file_fk FOREIGN KEY (generic_file_fk) REFERENCES public.generic_file(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: send_process_message send_process_message_send_process_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.send_process_message
    ADD CONSTRAINT send_process_message_send_process_fk FOREIGN KEY (send_process_fk) REFERENCES public.send_process(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: send_process send_process_pacs_user_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.send_process
    ADD CONSTRAINT send_process_pacs_user_fk FOREIGN KEY (pacs_user_fk) REFERENCES public.pacs_user(id) ON UPDATE SET NULL ON DELETE SET NULL;


--
-- Name: site_config site_cfg_entry_base_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.site_config
    ADD CONSTRAINT site_cfg_entry_base_fk FOREIGN KEY (config_entry_base_fk) REFERENCES public.config_entry_base(id) ON DELETE CASCADE;


--
-- Name: snapshot snapshot_created_by_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.snapshot
    ADD CONSTRAINT snapshot_created_by_fk FOREIGN KEY (created_by_fk) REFERENCES public.pacs_user(id) ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: stored_query stoque_nexque_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stored_query
    ADD CONSTRAINT stoque_nexque_fk FOREIGN KEY (next_query) REFERENCES public.stored_query(id);


--
-- Name: storage_commitment storage_commitment_aet_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.storage_commitment
    ADD CONSTRAINT storage_commitment_aet_fk FOREIGN KEY (aet_fk) REFERENCES public.aet(id);


--
-- Name: storage_commitment storage_commitment_dicom_image_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.storage_commitment
    ADD CONSTRAINT storage_commitment_dicom_image_fk FOREIGN KEY (dicom_image_fk) REFERENCES public.dicom_image(id) ON DELETE CASCADE;


--
-- Name: storage_rule storage_rule_compression_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.storage_rule
    ADD CONSTRAINT storage_rule_compression_fk FOREIGN KEY (tbl_compression_fk) REFERENCES public.tbl_compression(id);


--
-- Name: tbl_item_version_identifier tbl_item_version_identifier_tbl_item_version_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tbl_item_version_identifier
    ADD CONSTRAINT tbl_item_version_identifier_tbl_item_version_fk FOREIGN KEY (tbl_item_version_fk) REFERENCES public.tbl_item_version(id) ON DELETE CASCADE;


--
-- Name: tbl_instance tblinst_tblcont_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tbl_instance
    ADD CONSTRAINT tblinst_tblcont_fk FOREIGN KEY (tbl_container_fk) REFERENCES public.tbl_container(id) ON DELETE CASCADE;


--
-- Name: tbl_instance tblinst_tblnode_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tbl_instance
    ADD CONSTRAINT tblinst_tblnode_fk FOREIGN KEY (tbl_node_fk) REFERENCES public.tbl_node(id);


--
-- Name: tbl_item tblitem_tblcomp_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

-- ALTER TABLE ONLY public.tbl_item
--     ADD CONSTRAINT tblitem_tblcomp_fk FOREIGN KEY (tbl_compression_fk) REFERENCES public.tbl_compression(id);


--
-- Name: tbl_item tblitem_tblcont_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

-- ALTER TABLE ONLY public.tbl_item
--     ADD CONSTRAINT tblitem_tblcont_fk FOREIGN KEY (tbl_container_fk) REFERENCES public.tbl_container(id) ON DELETE CASCADE;


--
-- Name: tbl_item_version tblitemver_tblitem_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tbl_item_version
    ADD CONSTRAINT tblitemver_tblitem_fk FOREIGN KEY (tbl_item_fk) REFERENCES public.tbl_item(id) ON DELETE CASCADE;


--
-- Name: tbl_lock tbllock_tblcont_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tbl_lock
    ADD CONSTRAINT tbllock_tblcont_fk FOREIGN KEY (tbl_container_fk) REFERENCES public.tbl_container(id) ON DELETE CASCADE;


--
-- Name: treatment_detail treatment_detail_treatment_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.treatment_detail
    ADD CONSTRAINT treatment_detail_treatment_fk FOREIGN KEY (treatment_fk) REFERENCES public.treatment(id) ON DELETE CASCADE;


--
-- Name: treatment treatment_orgunit_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.treatment
    ADD CONSTRAINT treatment_orgunit_fk FOREIGN KEY (orgunit_fk) REFERENCES public.orgunit(id) ON DELETE CASCADE;


--
-- Name: treatment treatment_patient_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.treatment
    ADD CONSTRAINT treatment_patient_fk FOREIGN KEY (patient_fk) REFERENCES public.patient(id) ON DELETE CASCADE;


--
-- Name: user_config user_config_cfg_entry_base_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_config
    ADD CONSTRAINT user_config_cfg_entry_base_fk FOREIGN KEY (config_entry_base_fk) REFERENCES public.config_entry_base(id) ON DELETE CASCADE;


--
-- Name: view_by_pid view_by_pid_pacs_session_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.view_by_pid
    ADD CONSTRAINT view_by_pid_pacs_session_fk FOREIGN KEY (pacs_session_fk) REFERENCES public.pacs_session(id) ON DELETE CASCADE;


--
-- Name: visit vis_visinvbyfk_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.visit
    ADD CONSTRAINT vis_visinvbyfk_fk FOREIGN KEY (visit_invalidated_by_fk) REFERENCES public.visit(id);


--
-- Name: visit visit_dispos_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.visit
    ADD CONSTRAINT visit_dispos_fk FOREIGN KEY (discharge_disposition_fk) REFERENCES public.discharge_disposition(id);


--
-- Name: visit visit_modi_del_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.visit
    ADD CONSTRAINT visit_modi_del_fk FOREIGN KEY (deleted_by_fk) REFERENCES public.modifier(id);


--
-- Name: visit visit_modi_ins_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.visit
    ADD CONSTRAINT visit_modi_ins_fk FOREIGN KEY (inserted_by_fk) REFERENCES public.modifier(id);


--
-- Name: visit visit_modi_mod_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.visit
    ADD CONSTRAINT visit_modi_mod_fk FOREIGN KEY (last_modified_by_fk) REFERENCES public.modifier(id);


--
-- Name: visit visit_patcls_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.visit
    ADD CONSTRAINT visit_patcls_fk FOREIGN KEY (patient_class_fk) REFERENCES public.patient_class(id);


--
-- Name: visit visit_vipind_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.visit
    ADD CONSTRAINT visit_vipind_fk FOREIGN KEY (vip_indicator_fk) REFERENCES public.vip_indicator(id);


--
-- PostgreSQL database dump complete
--

