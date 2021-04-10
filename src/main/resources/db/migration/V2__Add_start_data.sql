-- Initialize statuses
insert into status (id, code) values ((select nextval('status_seq')), 'ENABLED');
insert into status (id, code) values ((select nextval('status_seq')), 'DISABLED');
insert into status (id, code) values ((select nextval('status_seq')), 'DELETED');

-- Initialize roles
insert into role (id, code) values ((select nextval('role_seq')), 'ROLE_ADMIN');
insert into role (id, code) values ((select nextval('role_seq')), 'ROLE_USER');

-- Add 'admin'.
insert into usr (id, username, password, email, status_id) values ((select nextval('usr_seq')), 'admin', 'admin', 'Gapusta97@gmail.com',(select id from status where code='ENABLED'));
insert into user_role(user_id, role_id) values (
    (select id from usr where username='admin'),
    (select id from role where code='ROLE_ADMIN')
);


