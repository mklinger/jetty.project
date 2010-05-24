// ========================================================================
// Copyright (c) 2009 Mort Bay Consulting Pty. Ltd.
// ------------------------------------------------------------------------
// All rights reserved. This program and the accompanying materials
// are made available under the terms of the Eclipse Public License v1.0
// and Apache License v2.0 which accompanies this distribution.
// The Eclipse Public License is available at 
// http://www.eclipse.org/legal/epl-v10.html
// The Apache License v2.0 is available at
// http://www.opensource.org/licenses/apache2.0.php
// You may elect to redistribute this code under either of these licenses. 
// ========================================================================

package org.eclipse.jetty.annotations;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.annotation.WebFilter;
import javax.servlet.annotation.WebInitParam;

import org.eclipse.jetty.annotations.AnnotationParser.DiscoverableAnnotationHandler;
import org.eclipse.jetty.annotations.AnnotationParser.Value;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.FilterMapping;
import org.eclipse.jetty.servlet.Holder;
import org.eclipse.jetty.util.Loader;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.webapp.WebAppContext;

public class WebFilterAnnotationHandler implements DiscoverableAnnotationHandler
{
  protected WebAppContext _wac;
    
    public WebFilterAnnotationHandler (WebAppContext wac)
    {
        _wac = wac;
    }
    
    public void handleClass(String className, int version, int access, String signature, String superName, String[] interfaces, String annotation,
                            List<Value> values)
    {
        Class clazz = null;
        try
        {
            clazz = Loader.loadClass(null, className);
        }
        catch (Exception e)
        {
            Log.warn(e);
            return;
        }
        
        //Servlet Spec 8.1.2
        if (!Filter.class.isAssignableFrom(clazz))
        {
            Log.warn(clazz.getName()+" is not assignable from javax.servlet.Filter");
            return;
        }
        
        WebFilter filterAnnotation = (WebFilter)clazz.getAnnotation(WebFilter.class);

        if (filterAnnotation.value().length > 0 && filterAnnotation.urlPatterns().length > 0)
        {
            Log.warn(clazz.getName()+" defines both @WebFilter.value and @WebFilter.urlPatterns");
            return;
        }

        String name = (filterAnnotation.filterName().equals("")?clazz.getName():filterAnnotation.filterName());
        String[] urlPatterns = filterAnnotation.value();
        if (urlPatterns.length == 0)
            urlPatterns = filterAnnotation.urlPatterns();
        
        FilterHolder holder = _wac.getServletHandler().getFilter(name);
        if (holder == null)
        {
            //Filter with this name does not already exist, so add it
            holder = _wac.getServletHandler().newFilterHolder(Holder.Source.ANNOTATION);
            holder.setHeldClass(clazz);
            holder.setName(name);
            holder.setDisplayName(filterAnnotation.displayName());

            for (WebInitParam ip:  filterAnnotation.initParams())
            {
                holder.setInitParameter(ip.name(), ip.value());
            }

            FilterMapping mapping = new FilterMapping();
            mapping.setFilterName(holder.getName());

            if (urlPatterns.length > 0)
            {
                ArrayList paths = new ArrayList();
                for (String s:urlPatterns)
                {
                    paths.add(Util.normalizePattern(s));
                }
                mapping.setPathSpecs((String[])paths.toArray(new String[paths.size()]));
            }

            if (filterAnnotation.servletNames().length > 0)
            {
                ArrayList<String> names = new ArrayList<String>();
                for (String s : filterAnnotation.servletNames())
                {
                    names.add(s);
                }
                mapping.setServletNames((String[])names.toArray(new String[names.size()]));
            }

            EnumSet<DispatcherType> dispatcherSet = EnumSet.noneOf(DispatcherType.class);           
            for (DispatcherType d : filterAnnotation.dispatcherTypes())
            {
                dispatcherSet.add(d);
            }
            mapping.setDispatcherTypes(dispatcherSet);

            holder.setAsyncSupported(filterAnnotation.asyncSupported());

            _wac.getServletHandler().addFilter(holder);
            _wac.getServletHandler().addFilterMapping(mapping);
        }
        else
        {
            //A Filter definition for the same name already exists from web.xml
            //ServletSpec 3.0 p81 if the Filter is already defined and has mappings,
            //they override the annotation. If it already has DispatcherType set, that
            //also overrides the annotation. Init-params are additive, but web.xml overrides
            //init-params of the same name.
            for (WebInitParam ip:  filterAnnotation.initParams())
            {
                if (holder.getInitParameter(ip.name()) == null)
                    holder.setInitParameter(ip.name(), ip.value());
            }
            
            FilterMapping[] mappings = _wac.getServletHandler().getFilterMappings();
            boolean mappingExists = false;
            if (mappings != null)
            {
                for (FilterMapping m:mappings)
                {
                    if (m.getFilterName().equals(name))
                    {
                        mappingExists = true;
                        break;
                    }
                }
            }
            //if the web.xml didn't specify at least one mapping, use the mappings from the annotation and the DispatcherTypes
            //from the annotation
            if (!mappingExists)
            {
                FilterMapping mapping = new FilterMapping();
                mapping.setFilterName(holder.getName());

                if (urlPatterns.length > 0)
                {
                    ArrayList paths = new ArrayList();
                    for (String s:urlPatterns)
                    {
                        paths.add(Util.normalizePattern(s));
                    }
                    mapping.setPathSpecs((String[])paths.toArray(new String[paths.size()]));
                }
                if (filterAnnotation.servletNames().length > 0)
                {
                    ArrayList<String> names = new ArrayList<String>();
                    for (String s : filterAnnotation.servletNames())
                    {
                        names.add(s);
                    }
                    mapping.setServletNames((String[])names.toArray(new String[names.size()]));
                }
                
                EnumSet<DispatcherType> dispatcherSet = EnumSet.noneOf(DispatcherType.class);           
                for (DispatcherType d : filterAnnotation.dispatcherTypes())
                {
                    dispatcherSet.add(d);
                }
                mapping.setDispatcherTypes(dispatcherSet);
                
                _wac.getServletHandler().addFilterMapping(mapping);          
            }
        }
    }

    public void handleField(String className, String fieldName, int access, String fieldType, String signature, Object value, String annotation,
                            List<Value> values)
    {
        Log.warn ("@WebFilter not applicable for fields: "+className+"."+fieldName);
    }

    public void handleMethod(String className, String methodName, int access, String params, String signature, String[] exceptions, String annotation,
                             List<Value> values)
    {
        Log.warn ("@WebFilter not applicable for methods: "+className+"."+methodName+" "+signature);
    }
    
}