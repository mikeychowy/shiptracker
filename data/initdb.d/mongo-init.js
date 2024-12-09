db = db.getSiblingDB("teqplay");

db.createUser({
    user: "user",
    pwd: "password",
    roles: [
        {
            role: 'readWrite',
            db: 'teqplay'
        },
    ],
});

db.createCollection("ships_latest_data");
db.createCollection("port_entry_events");
db.createCollection("port_exit_events");