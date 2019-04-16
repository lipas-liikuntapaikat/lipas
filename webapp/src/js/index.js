// Proj4
import proj4 from 'proj4';
window.proj4 = proj4;

// OpenLayers
import Map from 'ol/Map';
import View from 'ol/View';
import Overlay from 'ol/Overlay';
import Collection from 'ol/Collection';
import {get, fromLonLat, toLonLat} from 'ol/proj';
import {register} from 'ol/proj/proj4';
import Style from 'ol/style/Style';
import CircleStyle from 'ol/style/Circle';
import Fill from 'ol/style/Fill';
import Stroke from 'ol/style/Stroke';
import Icon from 'ol/style/Icon';
import RegularShape from 'ol/style/RegularShape';
import TileLayer from 'ol/layer/Tile';
import VectorLayer from 'ol/layer/Vector';
import WMTS from 'ol/source/WMTS';
import VectorSource from 'ol/source/Vector';
import {getTopLeft, getWidth, getHeight, extend} from 'ol/extent';
import GeoJSON from 'ol/format/GeoJSON';
import WMTSTileGrid from 'ol/tilegrid/WMTS';
import Draw from 'ol/interaction/Draw';
import Select from 'ol/interaction/Select';
import Modify from 'ol/interaction/Modify';
import Snap from 'ol/interaction/Snap';
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
    WMTS,
    Vector: VectorSource,
  },
  style: {
    Style,
    Circle: CircleStyle,
    Fill,
    Stroke,
    Icon,
    RegularShape,
  },
  extent: {
    getTopLeft,
    getWidth,
    getHeight,
    extend,
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
import MuiIcon from '@material-ui/core/Icon';
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
import Paper from '@material-ui/core/Paper';
import Popper from '@material-ui/core/Popper';
import Radio from '@material-ui/core/Radio';
import RadioGroup from '@material-ui/core/RadioGroup';
import MuiSelect from '@material-ui/core/Select';
import Slide from '@material-ui/core/Slide';
import Snackbar from '@material-ui/core/Snackbar';
import SnackbarContent from '@material-ui/core/SnackbarContent';
import Step from '@material-ui/core/Step';
import StepContent from '@material-ui/core/StepContent';
import StepLabel from '@material-ui/core/StepLabel';
import Stepper from '@material-ui/core/Stepper';
import SvgIcon from '@material-ui/core/SvgIcon';
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
import MuiTooltip from '@material-ui/core/Tooltip';
import Typography from '@material-ui/core/Typography';
import Zoom from '@material-ui/core/Zoom';
import CircularProgress from '@material-ui/core/CircularProgress';
import withWidth from '@material-ui/core/withWidth';

// Material-UI styles
import MuiThemeProvider from '@material-ui/core/styles/MuiThemeProvider';
import createMuiTheme from '@material-ui/core/styles/createMuiTheme';

window.mui = {
  // Styles
  createMuiTheme,
  MuiThemeProvider,
  // Core
  CssBaseline,
  AppBar,
  Toolbar,
  Typography,
  Icon: MuiIcon,
  SvgIcon,
  IconButton,
  TextField,
  Grid,
  Paper,
  Fab,
  Card,
  CardContent,
  CardHeader,
  CardActions,
  CardMedia,
  Chip,
  Menu,
  MenuItem,
  MenuList,
  Link,
  List,
  ListItem,
  ListItemIcon,
  ListItemText,
  ListItemSecondaryAction,
  Drawer,
  Divider,
  SwipeableDrawer,
  Tabs,
  Radio,
  RadioGroup,
  Tab,
  InputAdornment,
  FormControl,
  FormControlLabel,
  FormLabel,
  FormGroup,
  FormHelperText,
  Button,
  Hidden,
  Tooltip: MuiTooltip,
  Avatar,
  Checkbox,
  Table,
  TableHead,
  TableBody,
  TableRow,
  TableCell,
  TableSortLabel,
  TablePagination,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Snackbar,
  SnackbarContent,
  ExpansionPanel,
  ExpansionPanelActions,
  ExpansionPanelDetails,
  ExpansionPanelSummary,
  Stepper,
  Step,
  StepLabel,
  StepContent,
  InputLabel,
  Popper,
  Select: MuiSelect,
  Slide,
  Zoom,
  Fade,
  Grow,
  CircularProgress,
  withWidth,
};

// Recharts
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
import YAxis from 'recharts/lib/cartesian/YAxis';
import XAxis from 'recharts/lib/cartesian/XAxis';

window.recharts = {
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
  YAxis,
  XAxis,
};

// React-autosuggest
import Autosuggest from 'react-autosuggest';
window.Autosuggest = Autosuggest;

// Mui-Downshift
import MuiDownshift from 'mui-downshift';
window.MuiDownshift = MuiDownshift;

// Material Design Icons
import ContentCut from 'mdi-material-ui/ContentCut';
import Eraser from 'mdi-material-ui/Eraser';
import FileUpload from 'mdi-material-ui/FileUpload';
import MapSearchOutline from 'mdi-material-ui/MapSearchOutline';

window.materialIcons = {
  ContentCut,
  Eraser,
  FileUpload,
  MapSearchOutline,
};

// React virtualized
import VirtualizedList from 'react-virtualized/dist/commonjs/List';
import AutoSizer from 'react-virtualized/dist/commonjs/AutoSizer';
import InfiniteLoader from 'react-virtualized/dist/commonjs/InfiniteLoader';

window.reactVirtualized = {
  AutoSizer,
  InfiniteLoader,
  List: VirtualizedList,
};

// Turf.js
import kinks from '@turf/kinks';
import lineSplit from '@turf/line-split';
import lineIntersect from '@turf/line-intersect';
import combine from '@turf/combine';
import cleanCoords from '@turf/clean-coords';
import truncate from '@turf/truncate';
import nearestPointOnLine from '@turf/nearest-point-on-line';

window.turf = {
  kinks,
  lineSplit,
  lineIntersect,
  combine,
  cleanCoords,
  truncate,
  nearestPointOnLine,
};
