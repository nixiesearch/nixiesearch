from urllib.request import urlopen
import csv
import json
import sys
from io import StringIO

url = 'https://raw.githubusercontent.com/bahar/WorldCityLocations/refs/heads/master/World_Cities_Location_table.csv'
content = urlopen(url).read().decode("utf-8")
for row in csv.reader(StringIO(content), delimiter=';'):
	item = {"_id": row[0], "country": row[1], "city": row[2], "location": {"lat": float(row[3]), "lon": float(row[4])}}
	print(json.dumps(item))