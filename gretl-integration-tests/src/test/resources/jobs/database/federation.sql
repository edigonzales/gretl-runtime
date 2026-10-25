create schema result;
create table result.records as select * from src.colors order by id;
