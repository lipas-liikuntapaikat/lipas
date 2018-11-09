// OpenLayers
import {Map, View, Overlay} from "ol";
import Collection from "ol/Collection";
import {get, fromLonLat} from 'ol/proj';
import {register} from 'ol/proj/proj4';
import {Style, Circle, Fill, Stroke, Icon} from 'ol/style';
import {Tile as TileLayer, Vector as VectorLayer} from 'ol/layer';
import {WMTS as WMTSSource, Vector as VectorSource} from 'ol/source';
import {getTopLeft} from 'ol/extent';
import {GeoJSON} from 'ol/format';
import WMTSTileGrid from 'ol/tilegrid/WMTS';
import {Select, Draw, Modify, Snap} from 'ol/interaction';
import {pointerMove} from 'ol/events/condition';
import MultiPoint from 'ol/geom/MultiPoint.js';

// Openlayers-Extensions
import Splitter from 'ol-ext/interaction/Splitter';

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
  },
  extent: {
    getTopLeft,
  },
  proj: {
    get,
    fromLonLat,
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
  },
  events: {
    condition: {
      pointerMove,
    },
  },
  geom: {
    MultiPoint,
  },
};

// Zipcelx
import zipcelx from 'zipcelx-es5-cjs';
window.zipcelx = zipcelx;
