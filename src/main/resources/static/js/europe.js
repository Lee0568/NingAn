// ECharts Europe Map Data
(function (root, factory) {
    if (typeof exports === 'object') {
        module.exports = factory(require('echarts'));
    } else if (typeof define === 'function' && define.amd) {
        define(['echarts'], factory);
    } else {
        factory(root.echarts);
    }
}(this, function (echarts) {
    var log = function (msg) {
        if (typeof console !== 'undefined') {
            console && console.error && console.error(msg);
        }
    };
    if (!echarts) {
        log('ECharts is not Loaded');
        return;
    }
    if (!echarts.registerMap) {
        log('ECharts Map is not loaded');
        return;
    }
    echarts.registerMap('europe', {
        "type": "FeatureCollection",
        "features": [
            {
                "type": "Feature",
                "id": "EU",
                "properties": {
                    "name": "欧洲",
                    "cp": [10.0, 54.0]
                },
                "geometry": {
                    "type": "Polygon",
                    "coordinates": [[
                        [-10.0, 35.0], [-10.0, 71.0], [40.0, 71.0], 
                        [40.0, 35.0], [-10.0, 35.0]
                    ]]
                }
            }
        ]
    });
}));