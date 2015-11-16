<#-- @ftlvariable name="" type="info.voxtechnica.appraisers.view.SwaggerView" -->
<!DOCTYPE html>
<html>
<head>
  <title>Appraiser Service API</title>
  <link href='//fonts.googleapis.com/css?family=Droid+Sans:400,700' rel='stylesheet' type='text/css'/>
  <link href='${swaggerStaticPath}/css/highlight.default.css' media='screen' rel='stylesheet' type='text/css'/>
  <link href='${swaggerStaticPath}/css/screen.css' media='screen' rel='stylesheet' type='text/css'/>
  <script src="${swaggerStaticPath}/lib/shred.bundle.js" type="text/javascript"></script>
  <script src='${swaggerStaticPath}/lib/jquery-1.8.0.min.js' type='text/javascript'></script>
  <script src='${swaggerStaticPath}/lib/jquery.slideto.min.js' type='text/javascript'></script>
  <script src='${swaggerStaticPath}/lib/jquery.wiggle.min.js' type='text/javascript'></script>
  <script src='${swaggerStaticPath}/lib/jquery.ba-bbq.min.js' type='text/javascript'></script>
  <script src='${swaggerStaticPath}/lib/handlebars-1.0.0.js' type='text/javascript'></script>
  <script src='${swaggerStaticPath}/lib/underscore-min.js' type='text/javascript'></script>
  <script src='${swaggerStaticPath}/lib/backbone-min.js' type='text/javascript'></script>
  <script src='${swaggerStaticPath}/lib/swagger.js' type='text/javascript'></script>
  <script src='${swaggerStaticPath}/swagger-ui.js' type='text/javascript'></script>
  <script src='${swaggerStaticPath}/lib/highlight.7.3.pack.js' type='text/javascript'></script>
  <!-- For implicit OAuth2 scope support:
  <script src='${swaggerStaticPath}/lib/swagger-oauth.js' type='text/javascript'></script>
  -->
  <script type="text/javascript">
    $(function () {
      window.swaggerUi = new SwaggerUi({
        url: "${contextPath}/api-docs",
        dom_id: "swagger-ui-container",
        supportedSubmitMethods: ['get', 'post', 'put', 'delete'],
        onComplete: function(swaggerApi, swaggerUi){
          if(console) {
            console.log("Loaded SwaggerUI")
          }
          /*
          if(typeof initOAuth == "function") {
            initOAuth({
              clientId: "appraisers-api-ui",
              realm: "VoxTechnica",
              appName: "appraisers"
            });
          }
          */
          $('pre code').each(function(i, e) {
            hljs.highlightBlock(e)
          });
        },
        onFailure: function(data) {
          if(console) {
            console.log("Unable to Load SwaggerUI");
            console.log(data);
          }
        },
        docExpansion: "none",
        sorter: "alpha"
      });

      function addApiKeyAuthorization() {
        var key = $('#input_apiKey')[0].value;
        log("key: " + key);
        if(key && key.trim() != "") {
          log("added key " + key);
          window.authorizations.add("api_key", new ApiKeyAuthorization("Authorization", "Bearer " + key, "header"));
        }
      }
      $('#input_apiKey').change(function() {
        addApiKeyAuthorization();
      });
      
      /* Basic Authentication Support:
      var updateAuth = function() {
        var auth = "Basic " + btoa($('#input_user')[0].value + ":" + $('#input_pass')[0].value);
        window.authorizations.add("key", new ApiKeyAuthorization("Authorization", auth, "header"));
      };
      $('#input_user').change(updateAuth);
      $('#input_pass').change(updateAuth);
      */
      window.swaggerUi.load();
    });
  </script>
</head>

<body>
<div id='header'>
  <div class="swagger-ui-wrap">
    <a id="logo" href="https://appraisers.voxtechnica.info">Appraiser Service API</a>
    <form id='api_selector'>
      <div class='input'><input placeholder="oauth_bearer_token" id="input_apiKey" name="apiKey" type="text"/></div>
      <!-- Basic Authentication Support:
      <div class="input"><input placeholder="email" id="input_user" name="user" type="text" size="30"></div>
      <div class="input"><input placeholder="password" id="input_pass" name="pass" type="password" size="15"></div>
      -->
    </form>
  </div>
</div>
<div id="message-bar" class="swagger-ui-wrap">&nbsp;</div>
<div id="swagger-ui-container" class="swagger-ui-wrap"></div>
</body>
</html>