// ECharts China Map Data
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
    echarts.registerMap('china', {
        "type": "FeatureCollection",
        "features": [
            {
                "type": "Feature",
                "id": "CN",
                "properties": {
                    "name": "中国",
                    "cp": [104.195397, 35.86166]
                },
                "geometry": {
                    "type": "Polygon",
                    "coordinates": [[
                        [73.501, 39.452], [73.501, 53.558], [134.773, 53.558], 
                        [134.773, 18.197], [73.501, 18.197], [73.501, 39.452]
                    ]]
                }
            }
        ]
    });
}));