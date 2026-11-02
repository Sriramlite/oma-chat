import geopandas as gpd
import contextily as ctx
import matplotlib.pyplot as plt
from shapely.geometry import  Point,LineString,Polygon
data = gpd.read_file(
    '/home/linuxguru/Desktop/Sriram IT/content/gadm41_IND_shp/gadm41_IND_0.shp')
data = data.to_crs(epsg=3857)
ax=data.plot(figsize=(10,10),edgecolor='black')
ctx.add_basemap(ax,crs=data.crs,source=ctx.providers.OpenStreetMap.Mapnik)
path_to_data=gpd.datasets.get_path('nybb')
gdf=gpd.read_file(path_to_data)
gdf=gdf.set_index('BoroName')
gdf['area']=gdf.area
gdf['area']
plt.show()
            
