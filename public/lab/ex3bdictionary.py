import pandas as pd
population_dict={'california':38332521,'texes':26448193,'new york':19651127,'florida':19552860,'illinois':12882135}
population=pd.Series(population_dict)
print(population['california'])
print(population['california':'florida'])
