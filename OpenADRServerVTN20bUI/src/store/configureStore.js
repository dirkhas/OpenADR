import {createStore, compose, applyMiddleware} from 'redux';
import reduxImmutableStateInvariant from 'redux-immutable-state-invariant';
import thunk from 'redux-thunk';
import createHistory from 'history/createBrowserHistory';
// 'routerMiddleware': the new way of storing route changes with redux middleware since rrV4.
import { connectRouter, routerMiddleware } from 'connected-react-router';
import swaggerMiddleware from './swaggerMiddleware'
import swagger from 'swagger-client'
import rootReducer from '../reducers';


export const history = createHistory();
const connectRouterHistory = connectRouter(history);

var config = {
  vtnSwaggerUrl: "https://vtn.oadr.com:8181/testvtn/v2/api-docs"
};

function configureSwaggerMiddleware() {

  swagger.http.withCredentials = true
  const swaggerOpts = {
    url:config.vtnSwaggerUrl,
    success: function(e){
      console.log("Successfully connect to Swagger Backend")
      config.isConnectionPending = false; 
      config.isConnected = true;
    },
    failure: function(e){
      console.error("Can't connect to Swagger Backend");
      console.log(e);
      config.isConnectionPending = false; 
      config.isConnected = false; 
    }
  };
  return swaggerMiddleware(swaggerOpts);
}

function configureStoreProd(initialState) {
  const reactRouterMiddleware = routerMiddleware(history);


  const middlewares = [
    thunk,
    reactRouterMiddleware,
    configureSwaggerMiddleware(),
  ];

  return createStore(
    connectRouterHistory(rootReducer), 
    initialState, 
    compose(applyMiddleware(...middlewares))
  );
}

function configureStoreDev(initialState) {
  const reactRouterMiddleware = routerMiddleware(history);
  const middlewares = [
    reduxImmutableStateInvariant(),
    thunk,
    reactRouterMiddleware,
    configureSwaggerMiddleware(),
  ];

  const composeEnhancers = window.__REDUX_DEVTOOLS_EXTENSION_COMPOSE__ || compose; // add support for Redux dev tools
  const store = createStore(
    connectRouterHistory(rootReducer),  
    initialState, 
    composeEnhancers(applyMiddleware(...middlewares))
  );

  if (module.hot) {
    // Enable Webpack hot module replacement for reducers
    module.hot.accept('../reducers', () => {
      const nextRootReducer = require('../reducers').default; // eslint-disable-line global-require
      store.replaceReducer(connectRouterHistory(nextRootReducer));
    });
  }

  return store;
}

const configureStore = process.env.NODE_ENV === 'production' ? configureStoreProd : configureStoreDev;

export default configureStore;