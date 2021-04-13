create table access_level (
    id varchar(63) primary key,
    user_id integer constraint fk_access_level_user references usr(id) on delete cascade,
    file_id varchar(63) constraint fk_access_level_file references file(id) on delete cascade,
    level varchar(31)
);