ALTER TABLE spot_group_map
    DROP PRIMARY KEY,
    ADD PRIMARY KEY (spot_user_id, group_id);
