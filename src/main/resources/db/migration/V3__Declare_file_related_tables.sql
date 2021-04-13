
create table file (
    id varchar(63) not null primary key,
    name varchar(127) not null,
    ext varchar(63),
    size bigint not null,
    created_at timestamp not null,
    updated_at timestamp not null,
    owner_id integer constraint fk_folder_usr references usr(id) on delete cascade,
    status_id integer constraint fk_folder_status references status(id),
    file_type varchar(31) -- file, folder, shortcut etc
);

-- This is the so-called 'CLOSURE TABLE'. It is a way to store hierarchy data in relational databases
create table edge(
    id varchar(63) not null primary key,
    ancestor varchar(63) constraint fk_edge_ancestor_file references file (id) on delete cascade,
    descendant varchar(63) constraint fk_edge_descendant_file references file (id) on delete cascade,
    edge_type varchar(31) not null,
    edge_owner_id integer constraint fk_edge_user references usr(id) on delete cascade
);