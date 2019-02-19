/*
 * Inspired by the shp.js , dbf.js by Mano Marks
 *
 * I found there were something wrong to show chinese characters from DBF file,
 * so i added some code that is needed to deal with this problem.
 *
 * Created by Gipong <sheu781230@gmail.com>
 *
 */

var geojsonData = {};

// Shapefile parser, following the specification at
// http://www.esri.com/library/whitepapers/pdfs/shapefile.pdf
SHP = {
    NULL: 0,
    POINT: 1,
    POLYLINE: 3,
    POLYGON: 5
};

SHP.getShapeName = function(id) {
    for (name in this) {
        if (id === this[name]) {
            return name;
        }
    }
};

SHPParser = function() {};

SHPParser.load = function(url, callback, returnData) {
    var xhr = new XMLHttpRequest();
    xhr.open('GET', url);
    xhr.responseType = 'arraybuffer';
    xhr.onload = function() {
        geojsonData['shp'] = new SHPParser().parse(xhr.response,url);
        callback(geojsonData['shp'], returnData);
        URL.revokeObjectURL(url);
    };
    xhr.onerror = onerror;
    xhr.send(null);
};

SHPParser.prototype.parse = function(arrayBuffer,url) {
    var o = {},
        dv = new DataView(arrayBuffer),
        idx = 0;
    o.fileName = url;
    o.fileCode = dv.getInt32(idx, false);
    if (o.fileCode != 0x0000270a) {
        throw (new Error("Unknown file code: " + o.fileCode));
    }
    idx += 6*4;
    o.wordLength = dv.getInt32(idx, false);
    o.byteLength = o.wordLength * 2;
    idx += 4;
    o.version = dv.getInt32(idx, true);
    idx += 4;
    o.shapeType = dv.getInt32(idx, true);
    idx += 4;
    o.minX = dv.getFloat64(idx, true);
    o.minY = dv.getFloat64(idx+8, true);
    o.maxX = dv.getFloat64(idx+16, true);
    o.maxY = dv.getFloat64(idx+24, true);
    o.minZ = dv.getFloat64(idx+32, true);
    o.maxZ = dv.getFloat64(idx+40, true);
    o.minM = dv.getFloat64(idx+48, true);
    o.maxM = dv.getFloat64(idx+56, true);
    idx += 8*8;
    o.records = [];
    while (idx < o.byteLength) {
        var record = {};
        record.number = dv.getInt32(idx, false);
        idx += 4;
        record.length = dv.getInt32(idx, false);
        idx += 4;
        try {
            record.shape = this.parseShape(dv, idx, record.length);
        } catch(e) {
            console.log(e, record);
        }
        idx += record.length * 2;
        o.records.push(record);
    }
    return o;
};

SHPParser.prototype.parseShape = function(dv, idx, length) {
    var i=0,
        c=null,
        shape = {};
    shape.type = dv.getInt32(idx, true);
    idx += 4;
    var byteLen = length * 2;
    switch (shape.type) {
    case SHP.NULL: // Null
        break;

    case SHP.POINT: // Point (x,y)
        shape.content = {
            x: dv.getFloat64(idx, true),
            y: dv.getFloat64(idx+8, true)
        };
        break;
    case SHP.POLYLINE: // Polyline (MBR, partCount, pointCount, parts, points)
    case SHP.POLYGON: // Polygon (MBR, partCount, pointCount, parts, points)
        c = shape.content = {
            minX: dv.getFloat64(idx, true),
            minY: dv.getFloat64(idx+8, true),
            maxX: dv.getFloat64(idx+16, true),
            maxY: dv.getFloat64(idx+24, true),
            parts: new Int32Array(dv.getInt32(idx+32, true)),
            points: new Float64Array(dv.getInt32(idx+36, true)*2)
        };
        idx += 40;
        for (i=0; i<c.parts.length; i++) {
            c.parts[i] = dv.getInt32(idx, true);
            idx += 4;
        }
        for (i=0; i<c.points.length; i++) {
            c.points[i] = dv.getFloat64(idx, true);
            idx += 8;
        }
      break;

    case 8: // MultiPoint (MBR, pointCount, points)
    case 11: // PointZ (X, Y, Z, M)
    case 13: // PolylineZ
    case 15: // PolygonZ
    case 18: // MultiPointZ
    case 21: // PointM (X, Y, M)
    case 23: // PolylineM
    case 25: // PolygonM
    case 28: // MultiPointM
    case 31: // MultiPatch
        throw new Error("Shape type not supported: "
                      + shape.type + ':' +
                      + SHP.getShapeName(shape.type));
    default:
        throw new Error("Unknown shape type at " + (idx-4) + ': ' + shape.type);
    }
    return shape;
};

    /**
     * @fileoverview Parses a .dbf file based on the xbase standards as documented
     * here: http://www.clicketyclick.dk/databases/xbase/format/dbf.html
     * @author Mano Marks
     */

    // Creates global namespace.
DBF = {};

DBFParser = function() {};

DBFParser.load = function(url, encoding, callback, returnData) {
    var xhr = new XMLHttpRequest();
    xhr.open('GET', url);
    xhr.responseType = 'arraybuffer';

    xhr.onload = function() {

        var xhrText = new XMLHttpRequest();
        var xhrTextResponse = '';
        xhrText.open('GET', url);
        xhrText.overrideMimeType('text/plain; charset='+encoding);

        xhrText.onload = function() {
            geojsonData['dbf'] = new DBFParser().parse(xhr.response,url,xhrText.responseText,encoding);
            callback(geojsonData['dbf'], returnData);
            URL.revokeObjectURL(url);
        };

        xhrText.send();
    };
    xhr.onerror = onerror;
    xhr.send(null);

};

DBFParser.prototype.parse = function(arrayBuffer,src,response,encoding) {
    var o = {},
        dv = new DataView(arrayBuffer),
        idx = 0,
        offset = (encoding.match(/big5/i))?2:3;

    o.fileName = src;
    o.version = dv.getInt8(idx, false);

    idx += 1;
    o.year = dv.getUint8(idx) + 1900;
    idx += 1;
    o.month = dv.getUint8(idx);
    idx += 1;
    o.day = dv.getUint8(idx);
    idx += 1;

    o.numberOfRecords = dv.getInt32(idx, true);
    idx += 4;
    o.bytesInHeader = dv.getInt16(idx, true);
    idx += 2;
    o.bytesInRecord = dv.getInt16(idx, true);
    idx += 2;
    //reserved bytes
    idx += 2;
    o.incompleteTransation = dv.getUint8(idx);
    idx += 1;
    o.encryptionFlag = dv.getUint8(idx);
    idx += 1;
    // skip free record thread for LAN only
    idx += 4;
    // reserved for multi-user dBASE in dBASE III+
    idx += 8;
    o.mdxFlag = dv.getUint8(idx);
    idx += 1;
    o.languageDriverId = dv.getUint8(idx);
    idx += 1;
    // reserved bytes
    idx += 2;

    o.fields = [];

    var response_handler = response.split('\r');

    if(response_handler.length > 2) {
        response_handler.pop();
        responseHeader = response_handler.join('\r');
        responseHeader = responseHeader.slice(32, responseHeader.length);
    } else {
        responseHeader = response_handler[0];
        responseHeader = responseHeader.slice(32, responseHeader.length);
        offset = 2;
    }

    var charString = [],
        count = 0,
        index = 0,
        sum = (responseHeader.length+1)/32;

    while(responseHeader.length > 0) {
        while(count < 10) {
            try {
                if( encodeURIComponent(responseHeader[z]).match(/%[A-F\d]{2}/g) ) {
                    if( encodeURIComponent(responseHeader[z]).match(/%[A-F\d]{2}/g).length > 1 ) {
                        count += offset;
                        z++;
                    } else {
                        count += 1;
                        z++;
                    }
                } else {
                    count += 1;
                    z++;
                }
            } catch(error) { // avoid malformed URI
                count += 1;
                z++;
            }
        }

        charString.push(responseHeader.slice(0, 10).replace(/\0/g, ''))
        responseHeader =  responseHeader.slice(32, responseHeader.length);
    }

    while (true) {
        var field = {},
            nameArray = [];

        for (var i = 0, z=0; i < 10; i++) {
            var letter = dv.getUint8(idx);
            if (letter != 0) nameArray.push(String.fromCharCode(letter));
            idx += 1;
        }

        field.name = charString[index++];
        idx += 1;
        field.type = String.fromCharCode(dv.getUint8(idx));
        idx += 1;
        // Skip field data address
        idx += 4;
        field.fieldLength = dv.getUint8(idx);
        idx += 1;
        //field.decimalCount = dv.getUint8(idx);
        idx += 1;
        // Skip reserved bytes multi-user dBASE.
        idx += 2;
        field.workAreaId = dv.getUint8(idx);
        idx += 1;
        // Skip reserved bytes multi-user dBASE.
        idx += 2;
        field.setFieldFlag = dv.getUint8(idx);
        idx += 1;
        // Skip reserved bytes.
        idx += 7;
        field.indexFieldFlag = dv.getUint8(idx);
        idx += 1;
        o.fields.push(field);
        var test = dv.getUint8(idx);
        // Checks for end of field descriptor array. Valid .dbf files will have this
        // flag.
        if (dv.getUint8(idx) == 0x0D) break;
    }

    idx += 1;
    o.fieldpos = idx;
    o.records = [];

    responseText = response.split('\r')[response.split('\r').length-1];

    for (var i = 0; i < o.numberOfRecords; i++) {
        responseText = responseText.slice(1, responseText.length);
        var record = {};

        for (var j = 0; j < o.fields.length; j++) {
            var charString = [],
                count = 0,
                z = 0;

            while(count < o.fields[j].fieldLength) {
                try {
                    if( encodeURIComponent(responseText[z]).match(/%[A-F\d]{2}/g) ) {
                        if( encodeURIComponent(responseText[z]).match(/%[A-F\d]{2}/g).length > 1 ) {
                            count += offset;
                            z++;
                            check = 1;
                        } else {
                            count += 1;
                            z++;
                        }
                    } else {
                        count += 1;
                        z++;
                    }
                } catch(error) { // avoid malformed URI
                    count += 1;
                    z++;
                }
            }

            charString.push(responseText.slice(0, z).replace(/\0/g, ''));
            responseText =  responseText.slice(z, responseText.length);

            if(charString.join('').trim().match(/\d{1}\.\d{11}e\+\d{3}/g)) {
                record[o.fields[j].name] = parseFloat(charString.join('').trim());
            } else {
                record[o.fields[j].name] = charString.join('').trim();
            }

        }
        o.records.push(record);
    }
    return o;
};

/*
 * predefined [EPSG:3821] projection
 * Please make sure your desired projection can find on http://epsg.io/
 *
 * Usage :
 *      loadshp({
 *          url: '/shp/test.zip', // path or your upload file
 *          encoding: 'big5' // default utf-8
 *          EPSG: 3826 // default 4326
 *      }, function(geojson) {
 *          // geojson returned
 *      });
 *
 * Created by Gipong <sheu781230@gmail.com>
 *
 */

var inputData = {},
geoData = {},
EPSGUser, url, encoding, EPSG,
EPSG4326 = proj4('EPSG:4326');

function loadshp(config, returnData) {
    url = config.url;
    encoding = typeof config.encoding != 'utf-8' ? config.encoding : 'utf-8';
    EPSG = typeof config.EPSG != 'undefined' ? config.EPSG : 4326;

    loadEPSG('//epsg.io/'+EPSG+'.js', function() {
        if(EPSG == 3821)
            proj4.defs([
                ['EPSG:3821', '+proj=tmerc +ellps=GRS67 +towgs84=-752,-358,-179,-.0000011698,.0000018398,.0000009822,.00002329 +lat_0=0 +lon_0=121 +x_0=250000 +y_0=0 +k=0.9999 +units=m +no_defs']
            ]);

        EPSGUser = proj4('EPSG:'+EPSG);

        if(typeof url != 'string') {
            var reader = new FileReader();
            reader.onload = function(e) {
                var URL = window.URL || window.webkitURL || window.mozURL || window.msURL,
                zip = new JSZip(e.target.result),
                shpString =  zip.file(/.shp$/i)[0].name,
                dbfString = zip.file(/.dbf$/i)[0].name,
                prjString = zip.file(/.prj$/i)[0];
                if(prjString) {
                    proj4.defs('EPSGUSER', zip.file(prjString.name).asText());
                    try {
                      EPSGUser = proj4('EPSGUSER');
                    } catch (e) {
                      console.error('Unsuported Projection: ' + e);
                    }
                }

                SHPParser.load(URL.createObjectURL(new Blob([zip.file(shpString).asArrayBuffer()])), shpLoader, returnData);
                DBFParser.load(URL.createObjectURL(new Blob([zip.file(dbfString).asArrayBuffer()])), encoding, dbfLoader, returnData);
            }

            reader.readAsArrayBuffer(url);
        } else {
            JSZipUtils.getBinaryContent(url, function(err, data) {
                if(err) throw err;

                var URL = window.URL || window.webkitURL,
                zip = new JSZip(data),
                shpString =  zip.file(/.shp$/i)[0].name,
                dbfString = zip.file(/.dbf$/i)[0].name,
                prjString = zip.file(/.prj$/i)[0];
                if(prjString) {
                    proj4.defs('EPSGUSER', zip.file(prjString.name).asText());
                    try {
                      EPSGUser = proj4('EPSGUSER');
                    } catch (e) {
                      console.error('Unsuported Projection: ' + e);
                    }
                }

                SHPParser.load(URL.createObjectURL(new Blob([zip.file(shpString).asArrayBuffer()])), shpLoader, returnData);
                DBFParser.load(URL.createObjectURL(new Blob([zip.file(dbfString).asArrayBuffer()])), encoding, dbfLoader, returnData);

            });
        }
    });
}

function loadEPSG(url, callback) {
    var script = document.createElement('script');
    script.src = url;
    script.onreadystatechange = callback;
    script.onload = callback;
    document.getElementsByTagName('head')[0].appendChild(script);
}

function TransCoord(x, y) {
    if(proj4)
        var p = proj4(EPSGUser, EPSG4326 , [parseFloat(x), parseFloat(y)]);
    return {x: p[0], y: p[1]};
}

function shpLoader(data, returnData) {
    inputData['shp'] = data;
    if(inputData['shp'] && inputData['dbf'])
        if(returnData) returnData(  toGeojson(inputData)  );
}

function dbfLoader(data, returnData) {
    inputData['dbf'] = data;
    if(inputData['shp'] && inputData['dbf'])
        if(returnData) returnData(  toGeojson(inputData)  );
}

function toGeojson(geojsonData) {
    var geojson = {},
    features = [],
    feature, geometry, points;

    var shpRecords = geojsonData.shp.records;
    var dbfRecords = geojsonData.dbf.records;

    geojson.type = "FeatureCollection";
    min = TransCoord(geojsonData.shp.minX, geojsonData.shp.minY);
    max = TransCoord(geojsonData.shp.maxX, geojsonData.shp.maxY);
    geojson.bbox = [
        min.x,
        min.y,
        max.x,
        max.y
    ];

    geojson.features = features;

    for (var i = 0; i < shpRecords.length; i++) {
        feature = {};
        feature.type = 'Feature';
        geometry = feature.geometry = {};
        properties = feature.properties = dbfRecords[i];

        // point : 1 , polyline : 3 , polygon : 5, multipoint : 8
        switch(shpRecords[i].shape.type) {
            case 1:
                geometry.type = "Point";
                var reprj = TransCoord(shpRecords[i].shape.content.x, shpRecords[i].shape.content.y);
                geometry.coordinates = [
                    reprj.x, reprj.y
                ];
                break;
            case 3:
            case 8:
                geometry.type = (shpRecords[i].shape.type == 3 ? "LineString" : "MultiPoint");
                geometry.coordinates = [];
                for (var j = 0; j < shpRecords[i].shape.content.points.length; j+=2) {
                    var reprj = TransCoord(shpRecords[i].shape.content.points[j], shpRecords[i].shape.content.points[j+1]);
                    geometry.coordinates.push([reprj.x, reprj.y]);
                };
                break;
            case 5:
                geometry.type = "Polygon";
                geometry.coordinates = [];

                for (var pts = 0; pts < shpRecords[i].shape.content.parts.length; pts++) {
                    var partsIndex = shpRecords[i].shape.content.parts[pts],
                        part = [],
                        dataset;

                    for (var j = partsIndex*2; j < (shpRecords[i].shape.content.parts[pts+1]*2 || shpRecords[i].shape.content.points.length); j+=2) {
                        var point = shpRecords[i].shape.content.points;
                        var reprj = TransCoord(point[j], point[j+1]);
                        part.push([reprj.x, reprj.y]);
                    };
                    geometry.coordinates.push(part);

                };
                break;
            default:
        }
        if("coordinates" in feature.geometry) features.push(feature);
    };
    return geojson;
}

module.exports = { loadshp };
