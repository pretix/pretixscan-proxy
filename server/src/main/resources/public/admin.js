function err_handler(response) {
    app.loading--;
    if (response.headers["Content-Type"] === "application/json") {
        alert(response.body.title);
    } else {
        alert(response.statusText);
    }
}

var app = new Vue({
    // app initial state
    data: {
        state: {},
        config_url: "https://pretix.eu",
        config_token: "",
        loading: 0,
        init_data: null,
        addEventSlug: "",
    },

    created: function () {
        this.reload()
    },

    computed: {
        initqr: function () {
            if (this.init_data.token.startsWith("token="))
                return JSON.stringify(this.init_data);
        }
    },

    methods: {
        removeEvent: function (slug) {
            this.loading++;
            Vue.http.post("/proxyapi/v1/removeevent", {slug: slug}).then(function (response) {
                app.loading--;
                alert("Removed");
                app.reload()
            }, err_handler);
        },
        removeDevice: function (id) {
            this.loading++;
            Vue.http.post("/proxyapi/v1/remove", {uuid: id}).then(function (response) {
                app.loading--;
                alert("Removed");
                app.reload()
            }, err_handler);
        },
        addEvent: function () {
            this.loading++;
            Vue.http.post("/proxyapi/v1/addevent", {slug: this.addEventSlug}).then(function (response) {
                app.loading--;
                alert("Added");
                app.reload()
            }, err_handler);
        },
        sync: function () {
            this.loading++;
            Vue.http.post("/proxyapi/v1/sync").then(function (response) {
                app.loading--;
                alert("Sync complete");
                app.reload()
            }, err_handler);
        },
        synceventlist: function () {
            this.loading++;
            Vue.http.post("/proxyapi/v1/synceventlist").then(function (response) {
                app.loading--;
                alert("Sync complete");
                app.reload()
            }, err_handler);
        },
        reload: function () {
            this.loading++;
            Vue.http.get("/proxyapi/v1/state").then(function (response) {
                app.loading--;
                app.state = response.body;
            }, err_handler);
        },
        newdevice: function () {
            this.loading++;
            Vue.http.post("/proxyapi/v1/init").then(function (response) {
                app.loading--;
                app.init_data = response.body;
                app.reload();
            }, err_handler);
        },
        newproxydevice: function () {
            this.loading++;
            Vue.http.post("/proxyapi/v1/initready").then(function (response) {
                app.loading--;
                app.init_data = response.body;
                app.reload();
            }, err_handler);
        },
        configure: function () {
            this.loading++;
            Vue.http.post("/proxyapi/v1/configure", {
                "url": this.config_url,
                "token": this.config_token
            }).then(function (response) {
                app.loading--;
                app.reload()
            }, err_handler);
        }
    }
});

app.$mount('.adminapp');