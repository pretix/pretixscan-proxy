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
    },

    created: function () {
        this.reload()
    },

    computed: {
        initqr: function () {
            return JSON.stringify(this.init_data);
        }
    },

    methods: {
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