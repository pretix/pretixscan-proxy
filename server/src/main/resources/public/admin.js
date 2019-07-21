// app Vue instance
function err_handler(response) {
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
        config_token: ""
    },

    created: function () {
        this.reload()
    },

    methods: {
        reload: function () {
            Vue.http.get("/proxyapi/v1/state").then(function (response) {
                app.state = response.body;
            });
        },
        configure: function () {
            Vue.http.post("/proxyapi/v1/configure", {
                "url": this.config_url,
                "token": this.config_token
            }).then(function (response) {
                app.reload()
            }, err_handler);
        }
    }
});

app.$mount('.adminapp');