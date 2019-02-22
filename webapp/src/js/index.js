// OpenLayers
import {Map, View, Overlay} from "ol";
import Collection from "ol/Collection";
import {get, fromLonLat, toLonLat} from 'ol/proj';
import {register} from 'ol/proj/proj4';
import {Style, Circle, Fill, Stroke, Icon, RegularShape} from 'ol/style';
import {Tile as TileLayer, Vector as VectorLayer} from 'ol/layer';
import {WMTS as WMTSSource, Vector as VectorSource} from 'ol/source';
import {getTopLeft, getWidth, getHeight} from 'ol/extent';
import {GeoJSON} from 'ol/format';
import WMTSTileGrid from 'ol/tilegrid/WMTS';
import {Select, Draw, Modify, Snap} from 'ol/interaction';
import {pointerMove} from 'ol/events/condition';
import Point from 'ol/geom/Point';
import MultiPoint from 'ol/geom/MultiPoint';
import GeometryCollection from 'ol/geom/GeometryCollection';

// Openlayers-Extensions
import Splitter from 'ol-ext/interaction/Splitter';
import DrawHole from 'ol-ext/interaction/DrawHole';

window.ol = {
  Map, View, Overlay, Collection,
  layer: {
    Tile: TileLayer,
    Vector: VectorLayer,
  },
  source: {
    WMTS: WMTSSource,
    Vector: VectorSource,
  },
  style: {
    Style,
    Circle,
    Fill,
    Stroke,
    Icon,
    RegularShape,
  },
  extent: {
    getTopLeft,
    getWidth,
    getHeight,
  },
  proj: {
    get,
    fromLonLat,
    toLonLat,
    proj4: {
      register,
    },
  },
  tilegrid: {
    WMTS: WMTSTileGrid,
  },
  format: {
    GeoJSON,
  },
  interaction: {
    Select,
    Draw,
    Modify,
    Snap,
    Splitter,
    DrawHole,
  },
  events: {
    condition: {
      pointerMove,
    },
  },
  geom: {
    Point,
    MultiPoint,
    GeometryCollection,
  },
};

// Filesaver
import saveAs from 'file-saver';
window.filesaver = { saveAs };

// Zipcelx
import zipcelx from 'zipcelx-es5-cjs';
window.zipcelx = zipcelx;

// jszip
import JSZip from './jszip';
window.JSZip = JSZip;

// jszip-utils
import JSZipUtils from './jszip_utils';
window.JSZipUtils = JSZipUtils;

// shp2geojson
import { loadshp } from './shp2geojson';
window.shp2geojson = { loadshp };

// togeojson (KML,GPX)
import toGeoJSON from '@mapbox/togeojson';
window.toGeoJSON = toGeoJSON;

// togpx
import togpx from 'togpx';
window.togpx = togpx;
