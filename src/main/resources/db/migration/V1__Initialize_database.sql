create sequence status_seq start 1 increment 1;
create sequence role_seq start 1 increment 1;
create sequence usr_seq start 1 increment 1;

create table status (
    id integer not null primary key ,
    code varchar(255) not null
);

create table role(
    id integer not null primary key ,
    code varchar(255) not null
);

create table usr (
    id integer not null primary key ,
    username varchar(255) not null,
    password varchar(255) not null,
    email varchar(255) not null,
    status_id integer constraint fk_usr_status references status (id)
);

create table user_role (
    user_id int4 constraint fk_user_role_usr references usr(id) on delete cascade ,
    role_id int4 constraint fk_user_role_role references role(id) on delete cascade ,
    constraint pk_user_role primary key (user_id, role_id)
);