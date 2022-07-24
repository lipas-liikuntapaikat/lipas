// Proj4
import proj4 from 'proj4';
window.proj4 = proj4;

// OpenLayers
import Circle from 'ol/geom/Circle';
import CircleStyle from 'ol/style/Circle';
import Collection from 'ol/Collection';
import Draw from 'ol/interaction/Draw';
import Feature from 'ol/Feature';
import Fill from 'ol/style/Fill';
import GeoJSON from 'ol/format/GeoJSON';
import GeometryCollection from 'ol/geom/GeometryCollection';
import Icon from 'ol/style/Icon';
import Map from 'ol/Map';
import Modify from 'ol/interaction/Modify';
import MultiPoint from 'ol/geom/MultiPoint';
import Overlay from 'ol/Overlay';
import Point from 'ol/geom/Point';
import Polygon from 'ol/geom/Polygon';
import RegularShape from 'ol/style/RegularShape';
import Select from 'ol/interaction/Select';
import Snap from 'ol/interaction/Snap';
import Stroke from 'ol/style/Stroke';
import Style from 'ol/style/Style';
import TileLayer from 'ol/layer/Tile';
import ImageLayer from 'ol/layer/Image';
import VectorLayer from 'ol/layer/Vector';
import VectorSource from 'ol/source/Vector';
import View from 'ol/View';
import WMTS from 'ol/source/WMTS';
import TileWMS from 'ol/source/TileWMS';
import ImageWMS from 'ol/source/ImageWMS';
import WMTSTileGrid from 'ol/tilegrid/WMTS';
import { get, fromLonLat, toLonLat } from 'ol/proj';
import {
  boundingExtent,
  getTopLeft,
  getWidth,
  getHeight,
  extend,
  buffer,
  intersects,
} from 'ol/extent';
import { pointerMove } from 'ol/events/condition';
import { register } from 'ol/proj/proj4';
import WKT from 'ol/format/WKT';

// Openlayers-Extensions
import DrawHole from 'ol-ext/interaction/DrawHole';

window.ol = {
  Map,
  View,
  Overlay,
  Collection,
  Feature,
  layer: {
    Tile: TileLayer,
    Image: ImageLayer,
    Vector: VectorLayer,
  },
  source: {
    Vector: VectorSource,
    WMTS,
    TileWMS,
    ImageWMS,
  },
  style: {
    Circle: CircleStyle,
    Fill,
    Icon,
    RegularShape,
    Stroke,
    Style,
  },
  extent: {
    boundingExtent,
    buffer,
    extend,
    getHeight,
    getTopLeft,
    getWidth,
    intersects,
  },
  proj: {
    fromLonLat,
    get,
    proj4: {
      register,
    },
    toLonLat,
  },
  tilegrid: {
    WMTS: WMTSTileGrid,
  },
  format: {
    GeoJSON,
    WKT,
  },
  interaction: {
    Draw,
    DrawHole,
    Modify,
    Select,
    Snap,
  },
  events: {
    condition: {
      pointerMove,
    },
  },
  geom: {
    GeometryCollection,
    MultiPoint,
    Point,
    Polygon,
    Circle,
  },
};

// Filesaver
import saveAs from 'file-saver';
window.filesaver = { saveAs };

// Zipcelx
import zipcelx from 'zipcelx-es5-cjs';
window.zipcelx = zipcelx;

// jszip
// import JSZip from "./jszip";
// window.JSZip = JSZip;

// jszip-utils
// import JSZipUtils from "./jszip_utils";
// window.JSZipUtils = JSZipUtils;

// shp2geojson
// import { loadshp } from "./shp2geojson";
// window.shp2geojson = { loadshp };

// togeojson (KML,GPX)
import toGeoJSON from '@mapbox/togeojson';
window.toGeoJSON = toGeoJSON;

// togpx
import togpx from 'togpx';
window.togpx = togpx;

// React
import React from 'react';
import ReactDOM from 'react-dom';
import ReactDomServer from 'react-dom/server';

window.React = React;
window.ReactDOM = ReactDOM;
window.ReactDOMServer = ReactDomServer;

// MaterialUI
import AppBar from '@material-ui/core/AppBar';
import Avatar from '@material-ui/core/Avatar';
import Button from '@material-ui/core/Button';
import Card from '@material-ui/core/Card';
import CardActions from '@material-ui/core/CardActions';
import CardContent from '@material-ui/core/CardContent';
import CardHeader from '@material-ui/core/CardHeader';
import CardMedia from '@material-ui/core/CardMedia';
import Checkbox from '@material-ui/core/Checkbox';
import Chip from '@material-ui/core/Chip';
import CircularProgress from '@material-ui/core/CircularProgress';
import CssBaseline from '@material-ui/core/CssBaseline';
import Dialog from '@material-ui/core/Dialog';
import DialogActions from '@material-ui/core/DialogActions';
import DialogContent from '@material-ui/core/DialogContent';
import DialogTitle from '@material-ui/core/DialogTitle';
import Divider from '@material-ui/core/Divider';
import Drawer from '@material-ui/core/Drawer';
import ExpansionPanel from '@material-ui/core/ExpansionPanel';
import ExpansionPanelActions from '@material-ui/core/ExpansionPanelActions';
import ExpansionPanelDetails from '@material-ui/core/ExpansionPanelDetails';
import ExpansionPanelSummary from '@material-ui/core/ExpansionPanelSummary';
import Fab from '@material-ui/core/Fab';
import Fade from '@material-ui/core/Fade';
import FormControl from '@material-ui/core/FormControl';
import FormControlLabel from '@material-ui/core/FormControlLabel';
import FormGroup from '@material-ui/core/FormGroup';
import FormHelperText from '@material-ui/core/FormHelperText';
import FormLabel from '@material-ui/core/FormLabel';
import Grid from '@material-ui/core/Grid';
import Grow from '@material-ui/core/Grow';
import Hidden from '@material-ui/core/Hidden';
import IconButton from '@material-ui/core/IconButton';
import InputAdornment from '@material-ui/core/InputAdornment';
import InputLabel from '@material-ui/core/InputLabel';
import Link from '@material-ui/core/Link';
import List from '@material-ui/core/List';
import ListItem from '@material-ui/core/ListItem';
import ListItemIcon from '@material-ui/core/ListItemIcon';
import ListItemSecondaryAction from '@material-ui/core/ListItemSecondaryAction';
import ListItemText from '@material-ui/core/ListItemText';
import Menu from '@material-ui/core/Menu';
import MenuItem from '@material-ui/core/MenuItem';
import MenuList from '@material-ui/core/MenuList';
import MuiIcon from '@material-ui/core/Icon';
import MuiSelect from '@material-ui/core/Select';
import MuiTooltip from '@material-ui/core/Tooltip';
import Paper from '@material-ui/core/Paper';
import Popper from '@material-ui/core/Popper';
import Radio from '@material-ui/core/Radio';
import RadioGroup from '@material-ui/core/RadioGroup';
import Slide from '@material-ui/core/Slide';
import Snackbar from '@material-ui/core/Snackbar';
import SnackbarContent from '@material-ui/core/SnackbarContent';
import Step from '@material-ui/core/Step';
import StepContent from '@material-ui/core/StepContent';
import StepLabel from '@material-ui/core/StepLabel';
import Stepper from '@material-ui/core/Stepper';
import SvgIcon from '@material-ui/core/SvgIcon';
import Switch from '@material-ui/core/Switch';
import SwipeableDrawer from '@material-ui/core/SwipeableDrawer';
import Tab from '@material-ui/core/Tab';
import Table from '@material-ui/core/Table';
import TableBody from '@material-ui/core/TableBody';
import TableCell from '@material-ui/core/TableCell';
import TableHead from '@material-ui/core/TableHead';
import TablePagination from '@material-ui/core/TablePagination';
import TableRow from '@material-ui/core/TableRow';
import TableSortLabel from '@material-ui/core/TableSortLabel';
import Tabs from '@material-ui/core/Tabs';
import TextField from '@material-ui/core/TextField';
import Toolbar from '@material-ui/core/Toolbar';
import Typography from '@material-ui/core/Typography';
import Zoom from '@material-ui/core/Zoom';
import withWidth from '@material-ui/core/withWidth';

// Material-UI styles
import MuiThemeProvider from '@material-ui/core/styles/MuiThemeProvider';
import createMuiTheme from '@material-ui/core/styles/createMuiTheme';

window.mui = {
  // Styles
  createMuiTheme,
  MuiThemeProvider,
  // Core
  AppBar,
  Avatar,
  Button,
  Card,
  CardActions,
  CardContent,
  CardHeader,
  CardMedia,
  Checkbox,
  Chip,
  CircularProgress,
  CssBaseline,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  Drawer,
  ExpansionPanel,
  ExpansionPanelActions,
  ExpansionPanelDetails,
  ExpansionPanelSummary,
  Fab,
  Fade,
  FormControl,
  FormControlLabel,
  FormGroup,
  FormHelperText,
  FormLabel,
  Grid,
  Grow,
  Hidden,
  Icon: MuiIcon,
  IconButton,
  InputAdornment,
  InputLabel,
  Link,
  List,
  ListItem,
  ListItemIcon,
  ListItemSecondaryAction,
  ListItemText,
  Menu,
  MenuItem,
  MenuList,
  Paper,
  Popper,
  Radio,
  RadioGroup,
  Select: MuiSelect,
  Slide,
  Snackbar,
  SnackbarContent,
  Step,
  StepContent,
  StepLabel,
  Stepper,
  SvgIcon,
  SwipeableDrawer,
  Switch,
  Tab,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TablePagination,
  TableRow,
  TableSortLabel,
  Tabs,
  TextField,
  Toolbar,
  Tooltip: MuiTooltip,
  Typography,
  Zoom,
  withWidth,
};

// Recharts
import Area from 'recharts/lib/cartesian/Area';
import AreaChart from 'recharts/lib/chart/AreaChart';
import Bar from 'recharts/lib/cartesian/Bar';
import BarChart from 'recharts/lib/chart/BarChart';
import CartesianGrid from 'recharts/lib/cartesian/CartesianGrid';
import Cell from 'recharts/lib/component/Cell';
import ComposedChart from 'recharts/lib/chart/ComposedChart';
import LabelList from 'recharts/lib/component/LabelList';
import Legend from 'recharts/lib/component/Legend';
import Line from 'recharts/lib/cartesian/Line';
import LineChart from 'recharts/lib/chart/LineChart';
import Pie from 'recharts/lib/polar/Pie';
import PieChart from 'recharts/lib/chart/PieChart';
import ResponsiveContainer from 'recharts/lib/component/ResponsiveContainer';
import Tooltip from 'recharts/lib/component/Tooltip';
import XAxis from 'recharts/lib/cartesian/XAxis';
import YAxis from 'recharts/lib/cartesian/YAxis';

window.recharts = {
  Area,
  AreaChart,
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  ComposedChart,
  LabelList,
  Legend,
  Line,
  LineChart,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
};

// React-autosuggest
import Autosuggest from 'react-autosuggest';
window.Autosuggest = Autosuggest;

// react-select
import { SingleSelect, MultipleSelect } from 'react-select-material-ui';

window.ReactSelect = {
  SingleSelect,
  MultipleSelect,
};

// Material Design Icons
import Calculator from 'mdi-material-ui/Calculator';
import ContentCut from 'mdi-material-ui/ContentCut';
import ContentDuplicate from 'mdi-material-ui/ContentDuplicate';
import Eraser from 'mdi-material-ui/Eraser';
import FileUpload from 'mdi-material-ui/FileUpload';
import MapMarkerDistance from 'mdi-material-ui/MapMarkerDistance';
import MapSearchOutline from 'mdi-material-ui/MapSearchOutline';

window.materialIcons = {
  Calculator,
  ContentCut,
  ContentDuplicate,
  Eraser,
  FileUpload,
  MapMarkerDistance,
  MapSearchOutline,
};

// React virtualized
import AutoSizer from 'react-virtualized/dist/commonjs/AutoSizer';
import InfiniteLoader from 'react-virtualized/dist/commonjs/InfiniteLoader';
import VirtualizedList from 'react-virtualized/dist/commonjs/List';

window.reactVirtualized = {
  AutoSizer,
  InfiniteLoader,
  List: VirtualizedList,
};

// Turf.js
import bbox from '@turf/bbox';
import bufferTurf from '@turf/buffer';
import cleanCoords from '@turf/clean-coords';
import combine from '@turf/combine';
import kinks from '@turf/kinks';
import length from '@turf/length';
import lineIntersect from '@turf/line-intersect';
import lineSplit from '@turf/line-split';
import nearestPointOnLine from '@turf/nearest-point-on-line';
import truncate from '@turf/truncate';
import distance from '@turf/distance';
import { point } from '@turf/helpers';

window.turf = {
  bbox,
  buffer: bufferTurf,
  cleanCoords,
  combine,
  kinks,
  length,
  lineIntersect,
  lineSplit,
  nearestPointOnLine,
  truncate,
  distance,
  point,
};

// rc-slider
window.rcslider = {
  Slider: require('rc-slider'),
};

import shp from 'shpjs';

window.shp = shp;
