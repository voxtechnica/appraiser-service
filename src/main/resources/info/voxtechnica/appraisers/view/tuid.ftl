<#-- @ftlvariable name="" type="info.voxtechnica.appraisers.view.TuidView" -->
<html>
<head>
  <title>Tuid</title>
</head>
<body>
<p><b>TUID</b> (${tuid.class}): A time-based unique identifier, with millisecond resolution.</p>
<table cellpadding="5px" cellspacing="0">
  <tr><td align="right">id:</td><td>${tuid.id}</td></tr>
  <tr><td align="right">createdAt:</td><td>${tuid.createdAt}</td></tr>
  <tr><td align="right">millis:</td><td>${tuid.millis}</td></tr>
  <tr><td align="right">yearMonthDay:</td><td>${tuid.yearMonthDay?c}</td></tr>
  <tr><td align="right">yearMonth:</td><td>${tuid.yearMonth?c}</td></tr>
  <tr><td align="right">yearWeek:</td><td>${tuid.yearWeek?c}</td></tr>
  <tr><td align="right">yearDay:</td><td>${tuid.yearDay?c}</td></tr>
</table>
</body>
</html>