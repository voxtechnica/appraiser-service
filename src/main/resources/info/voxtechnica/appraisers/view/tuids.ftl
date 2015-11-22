<#-- @ftlvariable name="" type="info.voxtechnica.appraisers.view.TuidsView" -->
<!DOCTYPE html>
<html>
<head lang="en">
  <meta content="text/html;charset=UTF-8" http-equiv="content-type">
  <title>Tuids</title>
  <style>
    body {
      font-family: "Helvetica Neue", "Helvetica", Helvetica, Arial, sans-serif;
      font-weight: normal;
      font-style: normal;
      font-size: 90%;
      -webkit-font-smoothing: antialiased;
    }

    h1 {
      text-align: center;
    }

    table.tuids {
      border: 1px solid #d4d4d4;
      border-collapse: collapse;
      width: 100%;
    }

    table.tuids th, td {
      padding: 10px;
      border: 1px solid #d4d4d4;
      vertical-align: top;
    }

    table.tuids th {
      background-color: #d4d4d4;
      width: 40px;
    }

    .autoResizeImage {
      max-height: 200px;
      height: auto;
      width: auto;
    }
  </style>
</head>
<body>
<h1>TUIDs</h1>
<table class="tuids">
  <thead>
  <tr>
    <th>id</th>
    <th>createdAt</th>
    <th>millis</th>
    <th>yearMonthDay</th>
    <th>yearMonth</th>
    <th>yearWeek</th>
    <th>yearDay</th>
  </tr>
  </thead>
  <tbody>
  <#list tuids as tuid>
  <tr>
    <td><a href="/v1/tuids/${tuid.id}">${tuid.id}</a></td>
    <td>${tuid.createdAt}</td>
    <td>${tuid.millis?c}</td>
    <td>${tuid.yearMonthDay?c}</td>
    <td>${tuid.yearMonth?c}</td>
    <td>${tuid.yearWeek?c}</td>
    <td>${tuid.yearDay?c}</td>
  </tr>
  </#list>
  </tbody>
</table>
</body>
</html>
