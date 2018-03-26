mysql> select distinct Type from Posts;
+----------------------------+
| Type                       |
+----------------------------+
|                            |
| system_add_to_channel      |
| system_remove_from_channel |
| system_join_channel        |
| system_header_change       |
| system_channel_deleted     |
| system_displayname_change  |
| system_leave_channel       |
| system_purpose_change      |
+----------------------------+
9 rows in set (0.00 sec)

mysql> select Props from Posts where type='system_add_to_channel';
+----------------------------------------------+
| Props                                        |
+----------------------------------------------+
| {"addedUsername":"seth2","username":"seth"}  |
| {"addedUsername":"seth6","username":"seth5"} |
| {"addedUsername":"seth4","username":"seth"}  |
| {"addedUsername":"seth4","username":"seth"}  |
| {"addedUsername":"seth2","username":"seth"}  |
| {"addedUsername":"dave","username":"seth"}   |
| {"addedUsername":"dave","username":"seth2"}  |
| {"addedUsername":"dave","username":"seth"}   |
| {"addedUsername":"seth","username":"seth2"}  |
| {"addedUsername":"seth4","username":"seth2"} |
| {"addedUsername":"seth4","username":"seth"}  |
| {"addedUsername":"dave","username":"seth"}   |
| {"addedUsername":"seth","username":"seth2"}  |
| {"addedUsername":"seth2","username":"seth"}  |
| {"addedUsername":"seth4","username":"seth2"} |
| {"addedUsername":"seth","username":"seth2"}  |
| {"addedUsername":"dave","username":"seth"}   |
| {"addedUsername":"seth6","username":"seth5"} |
| {"addedUsername":"seth4","username":"seth"}  |
| {"addedUsername":"seth","username":"seth2"}  |
| {"addedUsername":"dave","username":"seth"}   |
| {"addedUsername":"seth4","username":"seth"}  |
| {"addedUsername":"seth4","username":"seth"}  |
| {"addedUsername":"dave","username":"seth"}   |
| {"addedUsername":"seth2","username":"seth"}  |
| {"addedUsername":"dave","username":"seth"}   |
| {"addedUsername":"seth4","username":"seth"}  |
| {"addedUsername":"seth2","username":"seth"}  |
| {"addedUsername":"apple","username":"seth2"} |
| {"addedUsername":"dave","username":"seth"}   |
| {"addedUsername":"dave","username":"seth"}   |
| {"addedUsername":"seth5","username":"seth"}  |
| {"addedUsername":"seth4","username":"seth"}  |
| {"addedUsername":"seth4","username":"seth"}  |
| {"addedUsername":"apple","username":"seth2"} |
| {"addedUsername":"seth2","username":"seth"}  |
| {"addedUsername":"seth","username":"seth2"}  |
| {"addedUsername":"dave","username":"seth"}   |
| {"addedUsername":"dave","username":"seth2"}  |
+----------------------------------------------+
39 rows in set (0.00 sec)

mysql> select distinct Props from Posts where type='system_add_to_channel';
+----------------------------------------------+
| Props                                        |
+----------------------------------------------+
| {"addedUsername":"seth2","username":"seth"}  |
| {"addedUsername":"seth6","username":"seth5"} |
| {"addedUsername":"seth4","username":"seth"}  |
| {"addedUsername":"dave","username":"seth"}   |
| {"addedUsername":"dave","username":"seth2"}  |
| {"addedUsername":"seth","username":"seth2"}  |
| {"addedUsername":"seth4","username":"seth2"} |
| {"addedUsername":"apple","username":"seth2"} |
| {"addedUsername":"seth5","username":"seth"}  |
+----------------------------------------------+
9 rows in set (0.00 sec)

mysql> select distinct Props from Posts where type='system_remove_from_channel';
+-----------------------------+
| Props                       |
+-----------------------------+
| {"removedUsername":"dave"}  |
| {"removedUsername":"seth4"} |
| {"removedUsername":"seth5"} |
+-----------------------------+
3 rows in set (0.00 sec)

mysql> select distinct Props from Posts where type='system_remove_from_channel';
+-----------------------------+
| Props                       |
+-----------------------------+
| {"removedUsername":"dave"}  |
| {"removedUsername":"seth4"} |
| {"removedUsername":"seth5"} |
+-----------------------------+
3 rows in set (0.00 sec)

mysql> select distinct Props from Posts where type='system_join_channel';
+----------------------+
| Props                |
+----------------------+
| {"username":"seth4"} |
| {"username":"seth2"} |
| {"username":"seth"}  |
| {"username":"seth5"} |
| {"username":"apple"} |
+----------------------+
5 rows in set (0.00 sec)

mysql> select distinct Props from Posts where type='system_header_change';
+-----------------------------------------------------------------------------------------------------------------+
| Props                                                                                                           |
+-----------------------------------------------------------------------------------------------------------------+
| {"new_header":"myDirectMessage","old_header":"","username":"seth"}                                              |
| {"new_header":"butt stuff and more stuff","old_header":"butt stuff","username":"seth"}                          |
| {"new_header":"where's the old header","old_header":"foobar","username":"seth"}                                 |
| {"new_header":"butt stuff and more stuff and stuff","old_header":"butt stuff and more stuff","username":"seth"} |
| {"new_header":"foobar","old_header":"","username":"seth"}                                                       |
+-----------------------------------------------------------------------------------------------------------------+
5 rows in set (0.01 sec)

mysql> select distinct Props from Posts where type='system_channel_deleted';
+---------------------+
| Props               |
+---------------------+
| {"username":"seth"} |
+---------------------+
1 row in set (0.00 sec)

mysql> select distinct Props from Posts where type='system_displayname_change';
+----------------------------------------------------------------------+
| Props                                                                |
+----------------------------------------------------------------------+
| {"new_displayname":"c3.0","old_displayname":"c3","username":"seth"}  |
| {"new_displayname":"c1.01","old_displayname":"c1","username":"seth"} |
+----------------------------------------------------------------------+
2 rows in set (0.00 sec)

mysql> select distinct Props from Posts where type='system_leave_channel';
+---------------------+
| Props               |
+---------------------+
| {"username":"seth"} |
+---------------------+
1 row in set (0.00 sec)

mysql> select distinct Props from Posts where type='system_purpose_change';
+--------------------------------------------------------------+
| Props                                                        |
+--------------------------------------------------------------+
| {"new_purpose":"flubbar","old_purpose":"","username":"seth"} |
+--------------------------------------------------------------+
1 row in set (0.00 sec)

mysql> select * from Posts where id='16ekctj4x3gcje7gqninz4kd9r';
+----------------------------+---------------+---------------+--------+----------+----------+----------------------------+----------------------------+--------+----------+------------+------------------------------------+-----------------------+---------------------------------------------+----------+-----------+---------+--------------+
| Id                         | CreateAt      | UpdateAt      | EditAt | DeleteAt | IsPinned | UserId                     | ChannelId                  | RootId | ParentId | OriginalId | Message                            | Type                  | Props                                       | Hashtags | Filenames | FileIds | HasReactions |
+----------------------------+---------------+---------------+--------+----------+----------+----------------------------+----------------------------+--------+----------+------------+------------------------------------+-----------------------+---------------------------------------------+----------+-----------+---------+--------------+
| 16ekctj4x3gcje7gqninz4kd9r | 1504833833486 | 1504833833486 |      0 |        0 |        0 | 7bz4c8jpqigtfp38gztk81mfmo | to4j94jxpjbojnqqazzn5sg33a |        |          |            | seth2 added to the channel by seth | system_add_to_channel | {"addedUsername":"seth2","username":"seth"} |          | []        | []      |            0 |
+----------------------------+---------------+---------------+--------+----------+----------+----------------------------+----------------------------+--------+----------+------------+------------------------------------+-----------------------+---------------------------------------------+----------+-----------+---------+--------------+
1 row in set (0.00 sec)

mysql> describe Posts;                                                                                                              +--------------+--------------+------+-----+---------+-------+
| Field        | Type         | Null | Key | Default | Extra |
+--------------+--------------+------+-----+---------+-------+
| Id           | varchar(26)  | NO   | PRI | NULL    |       |
| CreateAt     | bigint(20)   | YES  | MUL | NULL    |       |
| UpdateAt     | bigint(20)   | YES  | MUL | NULL    |       |
| EditAt       | bigint(20)   | YES  |     | NULL    |       |
| DeleteAt     | bigint(20)   | YES  | MUL | NULL    |       |
| IsPinned     | tinyint(1)   | YES  | MUL | NULL    |       |
| UserId       | varchar(26)  | YES  | MUL | NULL    |       |
| ChannelId    | varchar(26)  | YES  | MUL | NULL    |       |
| RootId       | varchar(26)  | YES  | MUL | NULL    |       |
| ParentId     | varchar(26)  | YES  |     | NULL    |       |
| OriginalId   | varchar(26)  | YES  |     | NULL    |       |
| Message      | text         | YES  | MUL | NULL    |       |
| Type         | varchar(26)  | YES  |     | NULL    |       |
| Props        | text         | YES  |     | NULL    |       |
| Hashtags     | text         | YES  | MUL | NULL    |       |
| Filenames    | text         | YES  |     | NULL    |       |
| FileIds      | varchar(150) | YES  |     | NULL    |       |
| HasReactions | tinyint(1)   | YES  |     | NULL    |       |
+--------------+--------------+------+-----+---------+-------+
18 rows in set (0.00 sec)

